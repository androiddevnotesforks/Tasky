package com.realityexpander.tasky.agenda_feature.presentation.event_screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realityexpander.tasky.R
import com.realityexpander.tasky.agenda_feature.domain.EventId
import com.realityexpander.tasky.agenda_feature.domain.AgendaItem
import com.realityexpander.tasky.agenda_feature.domain.Attendee
import com.realityexpander.tasky.agenda_feature.domain.IAgendaRepository
import com.realityexpander.tasky.agenda_feature.domain.Photo
import com.realityexpander.tasky.agenda_feature.presentation.common.util.max
import com.realityexpander.tasky.agenda_feature.presentation.common.util.min
import com.realityexpander.tasky.agenda_feature.presentation.event_screen.EventScreenEvent.*
import com.realityexpander.tasky.auth_feature.domain.IAuthRepository
import com.realityexpander.tasky.auth_feature.domain.validation.ValidateEmail
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
class EventViewModel @Inject constructor(
    private val authRepository: IAuthRepository,
    private val agendaRepository: IAgendaRepository,
    private val savedStateHandle: SavedStateHandle,
    private val validateEmail: ValidateEmail
) : ViewModel() {

    // Get savedStateHandle (after process death)
    private val errorMessage: UiText? =
        savedStateHandle[SavedStateConstants.SAVED_STATE_errorMessage]
    private val addAttendeeDialogErrorMessage: UiText? =
        savedStateHandle[SavedStateConstants.SAVED_STATE_addAttendeeDialogErrorMessage]
    private val isAttendeeEmailValid: Boolean? =
        savedStateHandle[SavedStateConstants.SAVED_STATE_isAttendeeEmailValid]
    private val editMode: EditMode? =
        savedStateHandle[SavedStateConstants.SAVED_STATE_editMode]
    private val savedEditedAgendaItem: AgendaItem.Event? =
        savedStateHandle[SavedStateConstants.SAVED_STATE_savedEditedAgendaItem]

    // Get params from savedStateHandle (from another screen)
    private val initialEventId: EventId? =
        savedStateHandle[SavedStateConstants.SAVED_STATE_initialEventId]
    private val isEditable: Boolean =
        savedStateHandle[SavedStateConstants.SAVED_STATE_isEditable] ?: false
    private val startDate: ZonedDateTime =
        savedStateHandle[SavedStateConstants.SAVED_STATE_startDate] ?: ZonedDateTime.now()

    private val _state = MutableStateFlow(
        EventScreenState(
            errorMessage = errorMessage,
            isProgressVisible = true,
            isEditable = isEditable,
            editMode = editMode,
            addAttendeeDialogErrorMessage = addAttendeeDialogErrorMessage,
            isAttendeeEmailValid = isAttendeeEmailValid,
            savedEditedAgendaItem = savedEditedAgendaItem
        )
    )
    val state =
        _state.onEach { state ->
            // save state for process death
            savedStateHandle[SavedStateConstants.SAVED_STATE_errorMessage] =
                state.errorMessage
            savedStateHandle[SavedStateConstants.SAVED_STATE_addAttendeeDialogErrorMessage] =
                state.addAttendeeDialogErrorMessage
            savedStateHandle[SavedStateConstants.SAVED_STATE_isAttendeeEmailValid] =
                state.isAttendeeEmailValid
            savedStateHandle[SavedStateConstants.SAVED_STATE_isEditable] =
                state.isEditable
            savedStateHandle[SavedStateConstants.SAVED_STATE_editMode] =
                state.editMode
            savedStateHandle[SavedStateConstants.SAVED_STATE_savedEditedAgendaItem] =
                state.event
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EventScreenState())

    private val _oneTimeEvent = MutableSharedFlow<OneTimeEvent>()
    val oneTimeEvent = _oneTimeEvent.asSharedFlow()

    init {
        viewModelScope.launch {

            val authInfo = authRepository.getAuthInfo()
            _state.update { _state ->
                _state.copy(
                    username = authInfo?.username ?: "",
                    authInfo = authInfo,
                )
            }

            // Check for return from process death
            savedEditedAgendaItem?.let {
                _state.update { _state ->
                    _state.copy(
                        isLoaded = true,
                        isProgressVisible = false,
                        event = savedEditedAgendaItem,
                    )
                }

                return@launch
            }

            // Check for Deeplink
            if (initialEventId != null) {
                val event = agendaRepository.getEvent(initialEventId)

                // if deeplink event is null, show error.
                event ?: run {
                    _state.value = _state.value.copy(
                        isProgressVisible = false,
                        errorMessage = UiText.Res(R.string.agenda_error_agenda_item_not_found)
                    )
                    return@launch
                }

                _state.update { _state ->
                    _state.copy(
                        isLoaded = true, // only after state is initialized
                        isProgressVisible = false,
                        event = event, // Apply current edits (from process death)
                    )
                }

                return@launch
            }

            // if no deeplink and initialEventId==null, then create new Event.
            _state.update { _state ->
                _state.copy(
                    isLoaded = true, // only after state is initialized
                    isProgressVisible = false,
                    event = AgendaItem.Event(
                        id = UUID.randomUUID().toString(),
                        title = "Title of New Event",
                        description = "Description of New Event",
                        from = startDate.withCurrentHourMinute(),
                        to = startDate.withCurrentHourMinute().plusHours(1),
                        remindAt = startDate.withCurrentHourMinute().minusMinutes(10),
                        host = authInfo?.userId,
                        isUserEventCreator = true,
                        photos = emptyList(),
                        attendees = listOf(
                            Attendee(
                                id = authInfo?.userId!!,
                                fullName = authInfo.username ?: "",
                                email = authInfo.email ?: "",
                                isGoing = true,
                            )
                        ),
                        isSynced = false,
                    )
                )
            }
        }
    }

    fun sendEvent(event: EventScreenEvent) {
        viewModelScope.launch {
            onEvent(event)
            yield() // allow events to percolate
        }
    }

    private suspend fun onEvent(uiEvent: EventScreenEvent) {

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
            is ValidateAttendeeEmail -> {
                _state.update { _state ->
                    _state.copy(
                        isAttendeeEmailValid =
                            if (uiEvent.email.isBlank())
                                null
                            else
                                validateEmail.validate(uiEvent.email)
                    )
                }
            }
            is ValidateAttendeeEmailExistsThenAddAttendee -> {
                sendEvent(ShowProgressIndicator(true))

                // call API to check if attendee email exists
                when (val result =
                    agendaRepository.validateAttendeeExists(uiEvent.email.trim().lowercase())
                ) {
                    is ResultUiText.Success<Attendee> -> {
                        val attendeeInfo = result.data
                        sendEvent(ShowProgressIndicator(false))

                        // Attempt Add Attendee to Event
                        attendeeInfo?.let { attendee ->

                            // Check if attendee is already in the list
                            val isAttendeeAlreadyInList =
                                _state.value.event?.attendees?.any { attendee.id == it.id }
                            if (isAttendeeAlreadyInList == true) {
                                sendEvent(SetErrorMessageForAddAttendeeDialog(
                                    UiText.Res(R.string.attendee_add_attendee_dialog_error_email_already_added))
                                )
                                return
                            }

                            // Add Attendee to Event
                            sendEvent(EditMode.AddAttendee(attendee))
                            sendEvent(CancelEditMode)
                            sendEvent(OneTimeEvent.ShowToast(UiText.Res(R.string.attendee_add_attendee_dialog_success)))
                        } ?: run {
                            sendEvent(SetErrorMessageForAddAttendeeDialog(UiText.Res(R.string.attendee_add_attendee_dialog_error_email_not_found)))
                        }
                    }
                    is ResultUiText.Error<Attendee> -> {
                        sendEvent(ShowProgressIndicator(false))
                        sendEvent(SetErrorMessageForAddAttendeeDialog(result.message))
                    }
                }
            }
            is SetErrorMessageForAddAttendeeDialog -> {
                _state.update { _state ->
                    _state.copy(
                        addAttendeeDialogErrorMessage = uiEvent.message
                    )
                }
            }
            is ClearErrorsForAddAttendeeDialog -> {
                _state.update { _state ->
                    _state.copy(
                        addAttendeeDialogErrorMessage = null,
                        isAttendeeEmailValid = null
                    )
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
                sendEvent(ClearErrorsForAddAttendeeDialog)
            }
            is EditMode.UpdateText -> {
                when (_state.value.editMode) {

                    is EditMode.ChooseTitleText -> {
                        _state.update { _state ->
                            _state.copy(
                                event = _state.event?.copy(title = uiEvent.text),
                                editMode = null,
                            )
                        }
                    }
                    is EditMode.ChooseDescriptionText -> {
                        _state.update { _state ->
                            _state.copy(
                                event = _state.event?.copy(description = uiEvent.text),
                                editMode = null,
                            )
                        }
                    }
                    else -> throw java.lang.IllegalStateException("Invalid type for SaveText: ${_state.value.editMode}")
                }
            }
            is EditMode.UpdateDateTime -> {
                when (_state.value.editMode) {

                    is EditMode.ChooseFromTime,
                    is EditMode.ChooseFromDate -> {
                        _state.update { _state ->
                            _state.event ?: throw IllegalStateException("Event is null")

                            val remindAtDuration =
                                Duration.between(_state.event.remindAt, _state.event.from)

                            val maxTo = max(_state.event.to, uiEvent.dateTime)
                            _state.copy(
                                event = _state.event.copy(
                                    from = uiEvent.dateTime,

                                    // Ensure that `to > from`
                                    to = maxTo, // max(_state.event.to, uiEvent.dateTime),

                                    // Update the `RemindAt dateTime` to keep same offset from the `From` date
                                    remindAt = uiEvent.dateTime.minus(remindAtDuration)
                                ),
                                editMode = null,
                            )
                        }
                    }
                    is EditMode.ChooseToTime,
                    is EditMode.ChooseToDate -> {
                        _state.update { _state ->

                            // Ensure that `from < to`
                            val minFrom =
                                min(_state.event?.from ?: ZonedDateTime.now(), uiEvent.dateTime)

                            val remindAtDuration =
                                Duration.between(_state.event?.remindAt, _state.event?.from)

                            _state.copy(
                                event = _state.event?.copy(
                                    to = uiEvent.dateTime,
                                    from = minFrom,

                                    // Update the `RemindAt dateTime` to keep same offset from the `From` date
                                    remindAt = minFrom.minus(remindAtDuration)
                                ),
                                editMode = null,
                            )
                        }
                    }
                    is EditMode.ChooseRemindAtDateTime -> {
                        _state.update { _state ->

                            // Ensure that `remindAt <= from`
                            if (uiEvent.dateTime.isAfter(_state.event?.from)) {
                                // make 'from' and 'remindAt' the same
                                return@update _state.copy(
                                    event = _state.event?.copy(
                                        remindAt = _state.event.from
                                    ),
                                    editMode = null
                                )
                            }

                            _state.copy(
                                event = _state.event?.copy(
                                    remindAt = uiEvent.dateTime
                                ),
                                editMode = null,
                            )
                        }
                    }
                    else -> throw java.lang.IllegalStateException("Invalid type for SaveDateTime: ${_state.value.editMode}")
                }
            }
            is EditMode.AddLocalPhoto -> {
                _state.update { _state ->
                    _state.copy(
                        event = _state.event?.copy(
                            photos = _state.event.photos + uiEvent.photoLocal
                        ),
                        editMode = null
                    )
                }
            }
            is EditMode.RemovePhoto -> {
                _state.update { _state ->
                    _state.copy(
                        editMode = null,
                        event = _state.event?.copy(
                            photos = _state.event.photos - uiEvent.photo,

                            // only add Remote Photos to list of deleted photo Ids
                            deletedPhotoIds = if(uiEvent.photo is Photo.Remote)
                                    _state.event.deletedPhotoIds + uiEvent.photo.id
                                else
                                    _state.event.deletedPhotoIds
                        ),
                    )
                }
            }
            is EditMode.AddAttendee -> {
                _state.update { _state ->
                    _state.copy(
                        event = _state.event?.copy(
                            attendees = _state.event.attendees +
                                    uiEvent.attendee.copy(isGoing = true)
                        )
                    )
                }
            }
            is EditMode.RemoveAttendee -> {
                _state.update { _state ->
                    _state.copy(
                        event = _state.event?.copy(
                            attendees = _state.event.attendees.filter { it.id != uiEvent.attendeeId }
                        ),
                    )
                }
                sendEvent(CancelEditMode)
            }
            OneTimeEvent.NavigateBack -> {
                _oneTimeEvent.emit(
                    OneTimeEvent.NavigateBack
                )
            }
            OneTimeEvent.LaunchPhotoPicker -> {
                _oneTimeEvent.emit(
                    OneTimeEvent.LaunchPhotoPicker
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
            is SaveEvent -> {
                val event = _state.value.event ?: return
                _state.update { _state ->
                    _state.copy(
                        isProgressVisible = true,
                        errorMessage = null
                    )
                }

                    when (initialEventId) {
                        null -> {
                            agendaRepository.createEvent(event)
                        }
                        else -> {
                            agendaRepository.updateEvent(event)
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
                            UiText.StrOrRes("Event saved", R.string.event_message_event_saved)
                        )
                    )
                    sendEvent(CancelEditMode)
                    sendEvent(OneTimeEvent.NavigateBack)
            }
            is DeleteEvent -> {
                _state.value.event ?: return

                _state.update { _state ->
                    _state.copy(
                        isProgressVisible = true,
                        errorMessage = null
                    )
                }

                if (initialEventId == null) {
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
                    agendaRepository.deleteEvent(_state.value.event ?: return)
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
                                UiText.Res(R.string.event_message_event_deleted_success)
                            )
                        )
                        sendEvent(CancelEditMode)
                        sendEvent(OneTimeEvent.NavigateBack)
                    }
                    is ResultUiText.Error -> {
                        _state.update { _state ->
                            _state.copy(
                                isProgressVisible = false,
                                errorMessage = UiText.Res(R.string.event_error_delete_event)
                            )
                        }
                    }
                }
            }
            is LeaveEvent -> {
                _state.update { _state ->
                    _state.copy(
                        event = _state.event?.copy(
                            attendees = _state.event.attendees.map {
                                if (it.id == state.value.authInfo?.userId) {
                                    it.copy(isGoing = false)
                                } else {
                                    it
                                }
                            },
                        )
                    )
                }
            }
            is JoinEvent -> {
                _state.update { _state ->
                    _state.copy(
                        event = _state.event?.copy(
                            attendees = _state.event.attendees.map {
                                if (it.id == state.value.authInfo?.userId) {
                                    it.copy(isGoing = true)
                                } else {
                                    it
                                }
                            },
                        )

                    )
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
