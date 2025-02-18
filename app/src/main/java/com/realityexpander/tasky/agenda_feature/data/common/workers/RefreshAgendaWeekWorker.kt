package com.realityexpander.tasky.agenda_feature.data.common.workers

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.realityexpander.tasky.R
import com.realityexpander.tasky.agenda_feature.data.common.utils.getDateForDayOffset
import com.realityexpander.tasky.agenda_feature.domain.IAgendaRepository
import com.realityexpander.tasky.agenda_feature.domain.IWorkerNotifications
import com.realityexpander.tasky.agenda_feature.domain.IWorkerScheduler
import com.realityexpander.tasky.core.presentation.util.ResultUiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import logcat.logcat
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// Worker to refresh Agenda data for 10 days around the `startDate`
@HiltWorker
class RefreshAgendaWeekWorker @AssistedInject constructor(
    @Assisted val context: Context,
    @Assisted val workerParams: WorkerParameters,
    private val agendaRepository: IAgendaRepository,
    private val workerNotifications: IWorkerNotifications
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORKER_NAME = "REFRESH_AGENDA_WEEK_WORKER"
        private const val NOTIFICATION_ID = 100001

        const val PARAMETER_START_DATE = "startDate"
        const val START_DAY_OFFSET = -5
        const val END_DAY_OFFSET = 5
    }

    override suspend fun doWork(): Result {
        logcat { "RefreshAgendaWeekWorker.doWork()" +  // leave this here for debugging
                " attemptedRuns: ${workerParams.runAttemptCount}" +
                " startDate: ${workerParams.inputData.getString(PARAMETER_START_DATE)}" }
        workerParams.log()

        val startDate = workerParams.inputData.getString(PARAMETER_START_DATE)?.let {
            getDateForDayOffset(ZonedDateTime.parse(it), 0)
        }

        // Fetch/Refresh the previous and coming week's Agenda items
        val success =

            // old way - (LEAVE FOR REFERENCE)
//            if (dayOffset != 0) { // don't refresh the current day
//                val date = getDateForDayOffset(startDate, dayOffset)
//                return@map CoroutineScope(Dispatchers.IO).async {
//                    agendaRepository.updateLocalAgendaDayFromRemote(date)
//                }
//            }
//            CoroutineScope(Dispatchers.IO).async {
//                ResultUiText.Success(Unit)
//            }

            supervisorScope {
                (START_DAY_OFFSET..END_DAY_OFFSET).map { dayOffset ->
                    async {
                        if (dayOffset != 0) { // don't refresh the current day
                           val date = getDateForDayOffset(startDate, dayOffset)
                            return@async agendaRepository.updateLocalAgendaDayFromRemote(date)
                        }

                        ResultUiText.Success(Unit)// default result is `Success` for the current day.
                    }
                }
            }
            //.awaitAll()  // will cancel all if any of the `async`s fail (LEAVE FOR REFERENCE)
            .mapIndexed { index, it -> // will NOT cancel all if any async fails (unlike .awaitAll())
                val success = it.await() is ResultUiText.Success // return true if success
                if (!success) {
                    logcat { "RefreshAgendaWeekWorker.doWork() - FAILED $index, $it" }
                }

                success
            }.all { success ->
                success == true   // if any of the async's failed, return `retry`
            }

        return if (success) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            RefreshAgendaWeekWorker.NOTIFICATION_ID,
            workerNotifications.createNotification(
                channelId = WORKER_NOTIFICATION_CHANNEL_ID,
                title = context.getString(R.string.agenda_sync_notification_title),
                description = context.getString(R.string.agenda_sync_notification_content_text),
                icon = R.drawable.ic_notification_sync_agenda_small_icon_foreground,
                iconTintColor = ResourcesCompat.getColor(
                    context.resources,
                    R.color.tasky_green,
                    null
                ),
                largeIcon = ResourcesCompat.getDrawable(
                    context.resources,
                    R.drawable.tasky_logo_for_splash,
                    null
                )?.toBitmap(100,100)
            )
        )
    }

    // Must use a separate class for the starter bc Dagger doesn't support @AssistedInject
    class WorkerScheduler @Inject constructor(
        private val context: Context
    ) : IWorkerScheduler {

        override fun startWorker() {
            // • Start the one-time 'Refresh Agenda Week' Worker
            val refreshAgendaWeekConstraints: Constraints = Constraints.Builder().apply {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }.build()
            val data = Data.Builder()
                .putString(
                    RefreshAgendaWeekWorker.PARAMETER_START_DATE,
                    ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toString()
                )
                .build()
            val name = RefreshAgendaWeekWorker.WORKER_NAME
            val agendaWeekWorkRequest =
                OneTimeWorkRequestBuilder<RefreshAgendaWeekWorker>()
                    .setConstraints(refreshAgendaWeekConstraints)
                    .setInputData(data)
                    .addTag(name)
                    .addTag("For 10 days around ${data.getString(RefreshAgendaWeekWorker.PARAMETER_START_DATE)}")
                    .addTag(AGENDA_WORKERS_TAG)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    name,
                    ExistingWorkPolicy.REPLACE,
                    agendaWeekWorkRequest
                )
        }

        override fun cancelWorker() {
            WorkManager.getInstance(context).cancelAllWorkByTag(SyncAgendaWorker.WORKER_NAME)
        }
    }
}