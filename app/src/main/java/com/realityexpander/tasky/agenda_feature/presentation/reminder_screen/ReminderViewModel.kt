package com.realityexpander.tasky.agenda_feature.presentation.reminder_screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realityexpander.tasky.R
import com.realityexpander.tasky.agenda_feature.domain.ReminderId
import com.realityexpander.tasky.agenda_feature.domain.AgendaItem
import com.realityexpander.tasky.agenda_feature.domain.IAgendaRepository
import com.realityexpander.tasky.agenda_feature.presentation.reminder_screen.ReminderScreenEvent.*
import com.realityexpander.tasky.auth_feature.domain.IAuthRepository
import com.realityexpander.tasky.core.presentation.common.SavedStateConstants
import com.realityexpander.tasky.core.presentation.util.ResultUiText
import com.realityexpander.tasky.core.presentation.util.UiText
import com.realityexpander.tasky.core.util.withCurrentHourMinute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val authRepository: IAuthRepository,
    private val agendaRepository: IAgendaRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Get savedStateHandle (after process death)
    private val errorMessage: UiText? =
        savedStateHandle[SavedStateConstants.SAVED_STATE_errorMessage]
    private val editMode: EditMode? =
        savedStateHandle[SavedStateConstants.SAVED_STATE_editMode]
    private val savedEditedAgendaItem: AgendaItem.Reminder? =
        savedStateHandle[SavedStateConstants.SAVED_STATE_savedEditedAgendaItem]

    // Get params from savedStateHandle (from another screen)
    private val initialReminderId: ReminderId? =
        savedStateHandle[SavedStateConstants.SAVED_STATE_initialReminderId]
    private val isEditable: Boolean =
        savedStateHandle[SavedStateConstants.SAVED_STATE_isEditable] ?: false
    private val startDate: ZonedDateTime =
        savedStateHandle[SavedStateConstants.SAVED_STATE_startDate] ?: ZonedDateTime.now()

    private val _state = MutableStateFlow(
        ReminderScreenState(
            errorMessage = errorMessage,
            isProgressVisible = true,
            isEditable = isEditable,
            editMode = editMode,
            savedEditedAgendaItem = savedEditedAgendaItem
        )
    )
    val state =
        _state.onEach { state ->
            // save state for process death
            savedStateHandle[SavedStateConstants.SAVED_STATE_errorMessage] =
                state.errorMessage
            savedStateHandle[SavedStateConstants.SAVED_STATE_isEditable] =
                state.isEditable
            savedStateHandle[SavedStateConstants.SAVED_STATE_editMode] =
                state.editMode
            savedStateHandle[SavedStateConstants.SAVED_STATE_savedEditedAgendaItem] =
                state.reminder
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderScreenState())

    private val _oneTimeEvent = MutableSharedFlow<OneTimeEvent>()
    val oneTimeEvent = _oneTimeEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            _state.update { _state ->
                val authInfo = authRepository.getAuthInfo()
                _state.copy(
                    isLoaded = true, // only after state is initialized
                    isProgressVisible = false,
                    username = authInfo?.username ?: "",
                    authInfo = authInfo,
                    reminder = savedEditedAgendaItem ?: run {
                        initialReminderId?.let { reminderId ->
                            agendaRepository.getReminder(reminderId) // Default Load item from repository
                        }
                            ?:
                            // If `initialReminderId` is null, then create a new Reminder.
                            // • CREATE A NEW Reminder
                            AgendaItem.Reminder(
                                id = UUID.randomUUID().toString(),
                                title = "Title of New Reminder",
                                description = "Description of New Reminder",
                                time = startDate.withCurrentHourMinute().plusHours(1),
                                remindAt = startDate.withCurrentHourMinute().plusMinutes(30),
                            )
                    }
                )
            }
        }
    }

    fun sendEvent(event: ReminderScreenEvent) {
        viewModelScope.launch {
            onEvent(event)
            yield() // allow events to percolate
        }
    }

    private suspend fun onEvent(uiEvent: ReminderScreenEvent) {

        when (uiEvent) {
            is ShowProgressIndicator -> {
                _state.update { _state ->
                    _state.copy(isProgressVisible = uiEvent.isVisible)
                }
                yield()
            }
            is SetIsLoaded -> {
                _state.update { _state ->
                    _state.copy(isProgressVisible = uiEvent.isLoaded)
                }
            }
            is SetIsEditable -> {
                _state.update { _state ->
                    _state.copy(isEditable = uiEvent.isEditable)
                }
            }
            is SetEditMode -> {
                _state.update { _state ->
                    _state.copy(editMode = uiEvent.editMode)
                }
            }
            is CancelEditMode -> {
                _state.update { _state ->
                    _state.copy(
                        editMode = null,
                    )
                }
            }
            is EditMode.UpdateText -> {
                when (_state.value.editMode) {

                    is EditMode.ChooseTitleText -> {
                        _state.update { _state ->
                            _state.copy(
                                reminder = _state.reminder?.copy(title = uiEvent.text),
                                editMode = null
                            )
                        }
                    }
                    is EditMode.ChooseDescriptionText -> {
                        _state.update { _state ->
                            _state.copy(
                                reminder = _state.reminder?.copy(description = uiEvent.text),
                                editMode = null
                            )
                        }
                    }
                    else -> throw java.lang.IllegalStateException("Invalid type for SaveText: ${_state.value.editMode}")
                }
            }
            is EditMode.UpdateDateTime -> {
                when (_state.value.editMode) {

                    is EditMode.ChooseTime,
                    is EditMode.ChooseDate -> {
                        _state.update { _state ->
                            val remindAtDuration =
                                Duration.between(_state.reminder?.remindAt, _state.reminder?.time)

                            _state.copy(
                                reminder = _state.reminder?.copy(
                                    time = uiEvent.dateTime,

                                    // Update the `RemindAt dateTime` to keep same offset from the `From` date
                                    remindAt = uiEvent.dateTime.minus(remindAtDuration)
                                ),
                                editMode = null
                            )
                        }
                    }
                    is EditMode.ChooseRemindAtDateTime -> {
                        _state.update { _state ->

                            // Ensure that `remindAt <= time`
                            if (uiEvent.dateTime.isAfter(_state.reminder?.time)) {
                                // make 'time' and 'remindAt' the same
                                return@update _state.copy(
                                    reminder = _state.reminder?.copy(
                                        remindAt = _state.reminder.time
                                    ),
                                    editMode = null
                                )
                            }

                            _state.copy(
                                reminder = _state.reminder?.copy(
                                    remindAt = uiEvent.dateTime
                                ),
                                editMode = null
                            )
                        }
                    }
                    else -> throw java.lang.IllegalStateException("Invalid type for SaveDateTime: ${_state.value.editMode}")
                }
            }
            OneTimeEvent.NavigateBack -> {
                _oneTimeEvent.emit(
                    OneTimeEvent.NavigateBack
                )
            }
            is ShowAlertDialog -> {
                _state.update { _state ->
                    _state.copy(
                        showAlertDialog =
                            ShowAlertDialog(
                                title = uiEvent.title,
                                message = uiEvent.message,
                                confirmButtonLabel = uiEvent.confirmButtonLabel,
                                onConfirm = uiEvent.onConfirm,
                                isDismissButtonVisible = uiEvent.isDismissButtonVisible,
                            )
                    )
                }
            }
            is DismissAlertDialog -> {
                _state.update { _state ->
                    _state.copy(
                        showAlertDialog = null
                    )
                }
            }
            is SaveReminder -> {
                _state.update { _state ->
                    _state.copy(
                        isProgressVisible = true,
                        errorMessage = null
                    )
                }

                when (initialReminderId) {
                    null -> {
                        agendaRepository.createReminder(_state.value.reminder ?: return)
                    }
                    else -> {
                        agendaRepository.updateReminder(_state.value.reminder ?: return)
                    }
                }

                _state.update { _state ->
                    _state.copy(
                        isProgressVisible = false,
                        errorMessage = null
                    )
                }
                _oneTimeEvent.emit(
                    OneTimeEvent.ShowToast(
                        UiText.Res(R.string.reminder_message_reminder_saved)
                    )
                )
                sendEvent(CancelEditMode)
                sendEvent(OneTimeEvent.NavigateBack)
            }
            is DeleteReminder -> {
                _state.value.reminder ?: return

                _state.update { _state ->
                    _state.copy(
                        isProgressVisible = true,
                        errorMessage = null
                    )
                }

                if (initialReminderId == null) {
                    // Event is not saved yet, so just navigate back
                    _state.update { _state ->
                        _state.copy(
                            isProgressVisible = false,
                            errorMessage = null
                        )
                    }
                    sendEvent(OneTimeEvent.NavigateBack)
                    return
                }

                val result =
                    agendaRepository.deleteReminder(_state.value.reminder ?: return)

                when (result) {
                    is ResultUiText.Success -> {
                        _state.update { _state ->
                            _state.copy(
                                isProgressVisible = false,
                                errorMessage = null
                            )
                        }
                        _oneTimeEvent.emit(
                            OneTimeEvent.ShowToast(
                                UiText.Res(R.string.task_message_task_deleted_success)
                            )
                        )
                        sendEvent(CancelEditMode)
                        sendEvent(OneTimeEvent.NavigateBack)
                    }
                    is ResultUiText.Error -> {
                        _state.update { _state ->
                            _state.copy(
                                isProgressVisible = false,
                                errorMessage = UiText.Res(R.string.task_error_delete_task)
                            )
                        }
                    }
                }
            }
            is ShowErrorMessage -> {
                _state.update { _state ->
                    _state.copy(
                        editMode = null,
                        isProgressVisible = false,
                        errorMessage = if (uiEvent.message.isRes)
                            uiEvent.message
                        else
                            UiText.Res(R.string.error_unknown, ""),
                    )
                }
            }
            is ClearErrorMessage -> {
                _state.update { _state ->
                    _state.copy(
                        errorMessage = null,
                    )
                }
            }
            else -> {}
        }
    }
}
