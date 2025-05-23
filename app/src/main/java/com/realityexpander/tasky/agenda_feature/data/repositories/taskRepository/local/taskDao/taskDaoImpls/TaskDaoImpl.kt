package com.realityexpander.tasky.agenda_feature.data.repositories.taskRepository.local.taskDao.taskDaoImpls

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.realityexpander.tasky.agenda_feature.domain.TaskId
import com.realityexpander.tasky.agenda_feature.data.repositories.taskRepository.local.ITaskDao
import com.realityexpander.tasky.agenda_feature.data.repositories.taskRepository.local.entities.TaskEntity
import com.realityexpander.tasky.core.util.DAY_IN_SECONDS
import kotlinx.coroutines.flow.Flow
import java.time.ZonedDateTime


@Dao
interface TaskDaoImpl : ITaskDao {

    // • CREATE

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun createTask(task: TaskEntity)


    // • UPSERT

    @Transaction
    override suspend fun upsertTask(task: TaskEntity) {
        val id = _insertTask(task)
        if (id == -1L) {
            _updateTask(task)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Suppress("FunctionName")
    suspend fun _insertTask(task: TaskEntity): Long

    @Update(onConflict = OnConflictStrategy.IGNORE)
    @Suppress("FunctionName")
    suspend fun _updateTask(task: TaskEntity)


    // • READ

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    override suspend fun getTaskById(taskId: TaskId): TaskEntity?

    @Query("SELECT * FROM tasks")
    override suspend fun getTasks(): List<TaskEntity>

    //
    @Query("SELECT * FROM tasks")
    override fun getTasksFlow(): Flow<List<TaskEntity>>

    @Query(GET_TASKS_FOR_DAY_QUERY)
    override suspend fun getTasksForDay(zonedDateTime: ZonedDateTime): List<TaskEntity>  // note: ZonedDateTime gets converted to UTC EpochSeconds for storage in the DB.

    @Query(GET_TASKS_FOR_DAY_QUERY)
    override fun getTasksForDayFlow(zonedDateTime: ZonedDateTime): Flow<List<TaskEntity>>  // note: ZonedDateTime gets converted to UTC EpochSeconds for storage in the DB.

    @Query(
        """
        SELECT * FROM tasks WHERE 
            ( remindAt >= :startDateTime) AND (remindAt < :endDateTime) -- remindAt starts within DateTime range
        """
    )
    override fun getTasksForRemindAtDateTimeRangeFlow(
        startDateTime: ZonedDateTime,
        endDateTime: ZonedDateTime
    ): Flow<List<TaskEntity>>  // note: ZonedDateTime gets converted to UTC EpochSeconds for storage in the DB.


    // • UPDATE

    @Update
    override suspend fun updateTask(task: TaskEntity): Int


    // • DELETE

    @Delete
    override suspend fun deleteTask(task: TaskEntity): Int

    @Query("DELETE FROM tasks WHERE id = :taskId")
    override suspend fun deleteTaskById(taskId: TaskId): Int

    @Query("DELETE FROM tasks WHERE id IN (:taskIds)")
    override suspend fun deleteTasksByIds(taskIds: List<TaskId>): Int

    @Query("DELETE FROM tasks")
    override suspend fun clearAllTasks(): Int

    // Deletes all SYNCED tasks for the given day.
    @Query(
        """
        DELETE FROM tasks WHERE
           isSynced = 1
           AND ( ( `time` >= :zonedDateTime) AND (`time` < :zonedDateTime + ${DAY_IN_SECONDS}) ) -- `time` start this today
        """)
    override suspend fun clearAllSyncedTasksForDay(zonedDateTime: ZonedDateTime): Int

    companion object {

        const val GET_TASKS_FOR_DAY_QUERY =
            """
            SELECT * FROM tasks WHERE 
                ( ( `time` >= :zonedDateTime) AND (`time` < :zonedDateTime + ${DAY_IN_SECONDS}) ) -- task starts this day
                
            """
    }
}
