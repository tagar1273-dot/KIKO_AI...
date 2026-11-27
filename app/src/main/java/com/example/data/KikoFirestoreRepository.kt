package com.example.data

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.model.ActivityLog
import com.example.model.ChatRecord
import com.example.model.DevProposal
import com.example.model.MemoryEntry
import com.example.model.SmartReminder
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class KikoFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun <T> Flow<T>.withFirestoreErrorHandling(
        uid: String,
        operationType: OperationType,
        path: String,
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User ID mismatch." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .withFirestoreErrorHandling(uid, OperationType.GET, path)
        )
    }

    suspend fun ensureUserProfile(userId: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(UserProfile(userId = uid).toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        userId: String,
        preferredLanguage: String,
        aiProvider: String,
        backendEndpoint: String,
        backendModel: String,
        voicePitch: Double,
        voiceRate: Double,
        continuousVoice: Boolean,
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val created = UserProfile(
                    userId = uid,
                    preferredLanguage = preferredLanguage,
                    aiProvider = aiProvider,
                    backendEndpoint = backendEndpoint,
                    backendModel = backendModel,
                    voicePitch = voicePitch,
                    voiceRate = voiceRate,
                    continuousVoice = continuousVoice,
                )
                docRef.set(created.toCreateMap(uid)).await()
            } else {
                docRef.update(
                    mapOf(
                        "preferredLanguage" to preferredLanguage,
                        "aiProvider" to aiProvider,
                        "backendEndpoint" to backendEndpoint.take(300),
                        "backendModel" to backendModel.ifBlank { "gemini-3.8-flash" }.take(100),
                        "voicePitch" to voicePitch.coerceIn(0.5, 2.0),
                        "voiceRate" to voiceRate.coerceIn(0.5, 2.0),
                        "continuousVoice" to continuousVoice,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeMemories(userId: String): Flow<List<MemoryEntry>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/memories"
        emitAll(
            db.collection("users").document(uid).collection("memories")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(MemoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .withFirestoreErrorHandling(uid, OperationType.LIST, path)
        )
    }

    suspend fun addMemory(entry: MemoryEntry): Result<Unit> {
        var path = "users/unknown/memories"
        return try {
            val uid = requireUserId()
            require(entry.title.isNotBlank()) { "Memory title cannot be blank." }
            require(entry.content.isNotBlank()) { "Memory content cannot be blank." }
            val docRef = db.collection("users").document(uid).collection("memories").document()
            path = docRef.path
            docRef.set(entry.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleMemoryPin(memoryId: String, pinned: Boolean): Result<Unit> {
        var path = "users/unknown/memories/$memoryId"
        return try {
            val uid = requireUserId()
            require(memoryId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        var path = "users/unknown/memories/$memoryId"
        return try {
            val uid = requireUserId()
            require(memoryId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeReminders(userId: String): Flow<List<SmartReminder>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/reminders"
        emitAll(
            db.collection("users").document(uid).collection("reminders")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SmartReminder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .withFirestoreErrorHandling(uid, OperationType.LIST, path)
        )
    }

    suspend fun addReminder(reminder: SmartReminder): Result<Unit> {
        var path = "users/unknown/reminders"
        return try {
            val uid = requireUserId()
            require(reminder.title.isNotBlank()) { "Reminder title cannot be blank." }
            val docRef = db.collection("users").document(uid).collection("reminders").document()
            path = docRef.path
            docRef.set(reminder.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleReminderCompleted(reminderId: String, completed: Boolean): Result<Unit> {
        var path = "users/unknown/reminders/$reminderId"
        return try {
            val uid = requireUserId()
            require(reminderId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("reminders").document(reminderId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteReminder(reminderId: String): Result<Unit> {
        var path = "users/unknown/reminders/$reminderId"
        return try {
            val uid = requireUserId()
            require(reminderId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("reminders").document(reminderId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeActivities(userId: String): Flow<List<ActivityLog>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/activities"
        emitAll(
            db.collection("users").document(uid).collection("activities")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(60)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ActivityLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .withFirestoreErrorHandling(uid, OperationType.LIST, path)
        )
    }

    suspend fun logActivity(activity: ActivityLog): Result<Unit> {
        var path = "users/unknown/activities"
        return try {
            val uid = requireUserId()
            require(activity.title.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("activities").document()
            path = docRef.path
            docRef.set(activity.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeChatMessages(userId: String): Flow<List<ChatRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/chat_messages"
        emitAll(
            db.collection("users").document(uid).collection("chat_messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limitToLast(100)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .withFirestoreErrorHandling(uid, OperationType.LIST, path)
        )
    }

    suspend fun addChatMessage(record: ChatRecord): Result<Unit> {
        var path = "users/unknown/chat_messages"
        return try {
            val uid = requireUserId()
            require(record.text.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("chat_messages").document()
            path = docRef.path
            docRef.set(record.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeDevProposals(userId: String): Flow<List<DevProposal>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/dev_proposals"
        emitAll(
            db.collection("users").document(uid).collection("dev_proposals")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DevProposal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .withFirestoreErrorHandling(uid, OperationType.LIST, path)
        )
    }

    suspend fun createDevProposal(proposal: DevProposal): Result<Unit> {
        var path = "users/unknown/dev_proposals"
        return try {
            val uid = requireUserId()
            require(proposal.title.isNotBlank())
            require(proposal.moduleName.isNotBlank())
            require(proposal.codePreview.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("dev_proposals").document()
            path = docRef.path
            docRef.set(proposal.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun decideDevProposal(proposalId: String, approved: Boolean): Result<Unit> {
        var path = "users/unknown/dev_proposals/$proposalId"
        return try {
            val uid = requireUserId()
            require(proposalId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("dev_proposals").document(proposalId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "status" to if (approved) "APPROVED" else "REJECTED",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
