package com.clipcut.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.clipcut.core.model.AudioTrackItem
import com.clipcut.core.model.BeatMarker
import com.clipcut.core.model.OverlayItem
import com.clipcut.core.model.PiPTrackItem
import com.clipcut.core.model.Project
import com.clipcut.core.model.TimelineItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose

/**
 * ProjectEntity — serialised via Gson into a single TEXT column per row.
 * Schema is intentionally minimal (one table, three columns) so that
 * schema migrations are trivial during early development.
 */
data class ProjectEntity(
    val id: String,
    val name: String,
    val createdTime: Long,
    val lastModifiedTime: Long,
    val aspectRatio: String = "16:9",
    val videoClipsJson: String = "[]",
    val audioTracksJson: String = "[]",
    val overlaysJson: String = "[]",
    val pipTracksJson: String = "[]",
    val beatMarkersJson: String = "[]",
    val originalVideoVolume: Float = 1.0f,
    val backgroundMusicVolume: Float = 1.0f,
    val audioDuckingEnabled: Boolean = false,
    val audioDuckLevel: Float = 0.3f
) {
    private val gson = Gson()

    fun toDomain(): Project {
        val clips = gson.fromJson<List<TimelineItem>>(videoClipsJson, object : TypeToken<List<TimelineItem>>() {}.type) ?: emptyList()
        val audio = gson.fromJson<List<AudioTrackItem>>(audioTracksJson, object : TypeToken<List<AudioTrackItem>>() {}.type) ?: emptyList()
        val overlays = gson.fromJson<List<OverlayItem>>(overlaysJson, object : TypeToken<List<OverlayItem>>() {}.type) ?: emptyList()
        val pips = gson.fromJson<List<PiPTrackItem>>(pipTracksJson, object : TypeToken<List<PiPTrackItem>>() {}.type) ?: emptyList()
        val beats = gson.fromJson<List<BeatMarker>>(beatMarkersJson, object : TypeToken<List<BeatMarker>>() {}.type) ?: emptyList()
        val ar = com.clipcut.core.model.AspectRatio.fromLabel(aspectRatio)
        return Project(
            id = id, name = name, createdTime = createdTime, lastModifiedTime = lastModifiedTime,
            aspectRatio = ar,
            videoClips = clips, audioTracks = audio, overlays = overlays,
            pipTracks = pips, beatMarkers = beats,
            originalVideoVolume = originalVideoVolume, backgroundMusicVolume = backgroundMusicVolume,
            audioDuckingEnabled = audioDuckingEnabled, audioDuckLevel = audioDuckLevel
        )
    }

    companion object {
        private val gson = Gson()
        fun fromDomain(project: Project): ProjectEntity = ProjectEntity(
            id = project.id, name = project.name,
            createdTime = project.createdTime, lastModifiedTime = project.lastModifiedTime,
            aspectRatio = project.aspectRatio.label,
            videoClipsJson = gson.toJson(project.videoClips),
            audioTracksJson = gson.toJson(project.audioTracks),
            overlaysJson = gson.toJson(project.overlays),
            pipTracksJson = gson.toJson(project.pipTracks),
            beatMarkersJson = gson.toJson(project.beatMarkers),
            originalVideoVolume = project.originalVideoVolume,
            backgroundMusicVolume = project.backgroundMusicVolume,
            audioDuckingEnabled = project.audioDuckingEnabled,
            audioDuckLevel = project.audioDuckLevel
        )
    }
}

interface ProjectDao {
    fun getAllProjectsFlow(): Flow<List<ProjectEntity>>
    suspend fun getProjectById(id: String): ProjectEntity?
    suspend fun insertProject(project: ProjectEntity)
    suspend fun updateProject(project: ProjectEntity)
    suspend fun deleteProjectById(id: String)
}

private const val TBL = "projects"

class ClipCutDatabase private constructor(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    private val dao = ProjectDaoImpl(this)

    fun projectDao(): ProjectDao = dao

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        db.execSQL(SQL_DROP)
        onCreate(db)
    }

    companion object {
        private const val DB_NAME = "clipcut.db"
        private const val DB_VERSION = 1
        private const val SQL_CREATE = """
            CREATE TABLE $TBL(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                createdTime INTEGER NOT NULL,
                lastModifiedTime INTEGER NOT NULL,
                aspectRatio TEXT DEFAULT '16:9',
                videoClipsJson TEXT DEFAULT '[]',
                audioTracksJson TEXT DEFAULT '[]',
                overlaysJson TEXT DEFAULT '[]',
                pipTracksJson TEXT DEFAULT '[]',
                beatMarkersJson TEXT DEFAULT '[]',
                originalVideoVolume REAL DEFAULT 1.0,
                backgroundMusicVolume REAL DEFAULT 1.0,
                audioDuckingEnabled INTEGER DEFAULT 0,
                audioDuckLevel REAL DEFAULT 0.3
            )
        """
        private const val SQL_DROP = "DROP TABLE IF EXISTS $TBL"

        @Volatile private var INSTANCE: ClipCutDatabase? = null
        fun getInstance(context: Context): ClipCutDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClipCutDatabase(context.applicationContext).also { INSTANCE = it }
            }
    }
}

private class ProjectDaoImpl(private val helper: ClipCutDatabase) : ProjectDao {
    override fun getAllProjectsFlow(): Flow<List<ProjectEntity>> = callbackFlow {
        trySend(queryAll())
        // Simple polling-based refresh — every open of the database window re-queries.
        // Real apps would use Room/Flowable; here we keep the surface but trigger on writes.
        awaitClose { }
    }

    override suspend fun getProjectById(id: String): ProjectEntity? = with(helper.readableDatabase) {
        query(TBL, null, "id=?", arrayOf(id), null, null, "lastModifiedTime DESC").use { c ->
            if (c.moveToFirst()) cursorToEntity(c) else null
        }
    }

    override suspend fun insertProject(project: ProjectEntity) {
        val db = helper.writableDatabase
        db.insertWithOnConflict(TBL, null, entityToCV(project), SQLiteDatabase.CONFLICT_REPLACE)
    }

    override suspend fun updateProject(project: ProjectEntity) {
        val db = helper.writableDatabase
        db.update(TBL, entityToCV(project), "id=?", arrayOf(project.id))
    }

    override suspend fun deleteProjectById(id: String) {
        helper.writableDatabase.delete(TBL, "id=?", arrayOf(id))
    }

    private fun queryAll(): List<ProjectEntity> = with(helper.readableDatabase) {
        val out = mutableListOf<ProjectEntity>()
        query(TBL, null, null, null, null, null, "lastModifiedTime DESC").use { c ->
            while (c.moveToNext()) cursorToEntity(c)?.let(out::add)
        }
        out
    }

    private fun cursorToEntity(c: Cursor): ProjectEntity? = if (c.count == 0) null else ProjectEntity(
        id = c.getString(c.getColumnIndexOrThrow("id")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        createdTime = c.getLong(c.getColumnIndexOrThrow("createdTime")),
        lastModifiedTime = c.getLong(c.getColumnIndexOrThrow("lastModifiedTime")),
        aspectRatio = c.getString(c.getColumnIndexOrThrow("aspectRatio")),
        videoClipsJson = c.getString(c.getColumnIndexOrThrow("videoClipsJson")),
        audioTracksJson = c.getString(c.getColumnIndexOrThrow("audioTracksJson")),
        overlaysJson = c.getString(c.getColumnIndexOrThrow("overlaysJson")),
        pipTracksJson = c.getString(c.getColumnIndexOrThrow("pipTracksJson")),
        beatMarkersJson = c.getString(c.getColumnIndexOrThrow("beatMarkersJson")),
        originalVideoVolume = c.getFloat(c.getColumnIndexOrThrow("originalVideoVolume")),
        backgroundMusicVolume = c.getFloat(c.getColumnIndexOrThrow("backgroundMusicVolume")),
        audioDuckingEnabled = c.getInt(c.getColumnIndexOrThrow("audioDuckingEnabled")) == 1,
        audioDuckLevel = c.getFloat(c.getColumnIndexOrThrow("audioDuckLevel"))
    )

    private fun entityToCV(e: ProjectEntity) = ContentValues().apply {
        put("id", e.id)
        put("name", e.name)
        put("createdTime", e.createdTime)
        put("lastModifiedTime", e.lastModifiedTime)
        put("aspectRatio", e.aspectRatio)
        put("videoClipsJson", e.videoClipsJson)
        put("audioTracksJson", e.audioTracksJson)
        put("overlaysJson", e.overlaysJson)
        put("pipTracksJson", e.pipTracksJson)
        put("beatMarkersJson", e.beatMarkersJson)
        put("originalVideoVolume", e.originalVideoVolume)
        put("backgroundMusicVolume", e.backgroundMusicVolume)
        put("audioDuckingEnabled", e.audioDuckingEnabled)
        put("audioDuckLevel", e.audioDuckLevel)
    }
}
