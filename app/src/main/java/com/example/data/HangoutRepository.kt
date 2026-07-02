package com.example.data

import kotlinx.coroutines.flow.Flow

class HangoutRepository(private val dao: HangoutDao) {

    // Profile
    val userProfile: Flow<UserProfileEntity?> = dao.getUserProfile()
    suspend fun getProfileSuspending(): UserProfileEntity? = dao.getUserProfileSuspending()
    suspend fun saveProfile(username: String, avatarIndex: Int, isLoggedIn: Boolean) {
        dao.insertOrUpdateUserProfile(UserProfileEntity(username = username, avatarIndex = avatarIndex, isLoggedIn = isLoggedIn))
    }

    suspend fun saveProfileWithTokens(username: String, avatarIndex: Int, isLoggedIn: Boolean, accessToken: String?, refreshToken: String?, role: String?) {
        val encryptedAccess = SecureKeyStoreHelper.encrypt(accessToken)
        val encryptedRefresh = SecureKeyStoreHelper.encrypt(refreshToken)
        dao.insertOrUpdateUserProfile(
            UserProfileEntity(
                username = username,
                avatarIndex = avatarIndex,
                isLoggedIn = isLoggedIn,
                accessToken = encryptedAccess,
                refreshToken = encryptedRefresh,
                role = role ?: "Member"
            )
        )
    }

    // Rooms
    val allRooms: Flow<List<RoomEntity>> = dao.getAllRooms()

    fun getRoom(roomId: String): Flow<RoomEntity?> = dao.getRoomById(roomId)
    suspend fun getRoomSuspending(roomId: String): RoomEntity? = dao.getRoomByIdSuspending(roomId)

    suspend fun createOrUpdateRoom(room: RoomEntity) {
        dao.insertOrUpdateRoom(room)
    }

    suspend fun deleteRoom(roomId: String) {
        dao.deleteRoomById(roomId)
        dao.deleteMessagesForRoom(roomId)
    }

    // Messages
    fun getMessages(roomId: String): Flow<List<MessageEntity>> = dao.getMessagesForRoom(roomId)

    suspend fun getMessageById(messageId: Long): MessageEntity? = dao.getMessageByIdSuspending(messageId)

    suspend fun insertMessage(message: MessageEntity) {
        val count = dao.getMessageDuplicateCount(
            roomId = message.roomId,
            senderName = message.senderName,
            content = message.content,
            timestamp = message.timestamp
        )
        if (count == 0) {
            dao.insertMessage(message)
        }
    }

    suspend fun sendMessage(roomId: String, senderName: String, senderAvatarIndex: Int, content: String, isSystem: Boolean = false) {
        insertMessage(
            MessageEntity(
                roomId = roomId,
                senderName = senderName,
                senderAvatarIndex = senderAvatarIndex,
                content = content,
                isSystem = isSystem
            )
        )
    }
}
