package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HangoutDao {

    // Rooms
    @Query("SELECT * FROM rooms ORDER BY createdAt DESC")
    fun getAllRooms(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE id = :roomId")
    fun getRoomById(roomId: String): Flow<RoomEntity?>

    @Query("SELECT * FROM rooms WHERE id = :roomId")
    suspend fun getRoomByIdSuspending(roomId: String): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRoom(room: RoomEntity)

    @Query("DELETE FROM rooms WHERE id = :roomId")
    suspend fun deleteRoomById(roomId: String)

    // Messages
    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    fun getMessagesForRoom(roomId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT COUNT(*) FROM messages WHERE roomId = :roomId AND senderName = :senderName AND content = :content AND timestamp = :timestamp")
    suspend fun getMessageDuplicateCount(roomId: String, senderName: String, content: String, timestamp: Long): Int

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageByIdSuspending(messageId: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE roomId = :roomId")
    suspend fun deleteMessagesForRoom(roomId: String)

    // User Profile
    @Query("SELECT * FROM user_profile WHERE id = 'current_user'")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 'current_user'")
    suspend fun getUserProfileSuspending(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUserProfile(user: UserProfileEntity)
}
