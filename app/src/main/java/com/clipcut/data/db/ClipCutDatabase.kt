package com.clipcut.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.AudioTrackItem
import com.clipcut.core.model.BeatMarker
import com.clipcut.core.model.OverlayItem
import com.clipcut.core.model.PiPTrackItem
import com.clipcut.core.model.TimelineItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ClipCutDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, "clipcut.db", null, 4) {

    private val gson = Gson()
    private val _projectsFlow = MutableStateFlow<List<ProjectEntity>>(emptyList())

    init { refreshFlow() }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS projects (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                createdTime INTEGER NOT NULL,
                lastModifiedTime INTEGER NOT NULL,
                aspectRatio TEXT NOT NULL DEFAULT '16:9',
                videoClips TEXT NOT NULL,
                audioTracks TEXT NOT NULL,
                overlays TEXT NOT NULL,
                pipTracks TEXT NOT NULL DEFAULT '[]',
                beatMarkers TEXT NOT NULL DEFAULT '[]',
                originalVideoVolume REAL NOT NULL,
                backgroundMusicVolume REAL NOT NULL,
                audioDuckingEnabled INTEGER NOT NULL DEFAULT 0,
                audioDuckLevel REAL NOT NULL DEFAULT 0.3
            )
        """.trimIndent())
        AppLog.i("ClipCutDatabase", "SQLite projects table created (v4)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        AppLog.w("ClipCutDatabase", "DB upgrade $oldVersion -> $newVersion")
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE projects ADD COLUMN pipTracks TEXT NOT NULL DEFAULT '[]'")
                AppLog.i("ClipCutDatabase", "Added pipTracks column to projects table")
            } catch (e: Exception) {
                AppLog.e("ClipCutDatabase", "ALTER TABLE pipTracks failed", e)
            }
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE projects ADD COLUMN aspectRatio TEXT NOT NULL DEFAULT '16:9'")
                AppLog.i("ClipCutDatabase", "Added aspectRatio column to projects table")
            } catch (e: Exception) {
                AppLog.e("ClipCutDatabase", "ALTER TABLE aspectRatio failed", e)
            }
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE projects ADD COLUMN beatMarkers TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE projects ADD COLUMN audioDuckingEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN audioDuckLevel REAL NOT NULL DEFAULT 0.3")
                AppLog.i("ClipCutDatabase", "Added beatMarkers and audio ducking columns to projects table")
            } catch (e: Exception) {
                AppLog.e("ClipCutDatabase", "ALTER TABLE v4 columns failed", e)
            }
        }
    }

    private fun refreshFlow() {
        try { _projectsFlow.value = queryAllProjects() }
        catch (e: Exception) { AppLog.e("ClipCutDatabase", "Error refreshing", e) }
    }

    private fun rowToEntity(cursor: android.database.Cursor): ProjectEntity {
        val timelineType = object : TypeToken<List<TimelineItem>>() {}.type
        val audioType = object : TypeToken<List<AudioTrackItem>>() {}.type
        val overlayType = object : TypeToken<List<OverlayItem>>() {}.type
        val pipType = object : TypeToken<List<PiPTrackItem>>() {}.type
        val beatType = object : TypeToken<List<BeatMarker>>() {}.type

        val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
        val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
        val created = cursor.getLong(cursor.getColumnIndexOrThrow("createdTime"))
        val modified = cursor.getLong(cursor.getColumnIndexOrThrow("lastModifiedTime"))
        val vJson = cursor.getString(cursor.getColumnIndexOrThrow("videoClips"))
        val aJson = cursor.getString(cursor.getColumnIndexOrThrow("audioTracks"))
        val oJson = cursor.getString(cursor.getColumnIndexOrThrow("overlays"))
        val pipIdx = cursor.getColumnIndex("pipTracks")
        val pipJson = if (pipIdx >= 0) cursor.getString(pipIdx) else "[]"
        val arIdx = cursor.getColumnIndex("aspectRatio")
        val arStr = if (arIdx >= 0) cursor.getString(arIdx) else "16:9"
        val bmIdx = cursor.getColumnIndex("beatMarkers")
        val bmJson = if (bmIdx >= 0) cursor.getString(bmIdx) else "[]"
        val origVol = cursor.getFloat(cursor.getColumnIndexOrThrow("originalVideoVolume"))
        val bgVol = cursor.getFloat(cursor.getColumnIndexOrThrow("backgroundMusicVolume"))
        val duckIdx = cursor.getColumnIndex("audioDuckingEnabled")
        val duckEnabled = if (duckIdx >= 0) cursor.getInt(duckIdx) == 1 else false
        val duckLvlIdx = cursor.getColumnIndex("audioDuckLevel")
        val duckLevel = if (duckLvlIdx >= 0) cursor.getFloat(duckLvlIdx) else 0.3f

        return ProjectEntity(
            id = id, name = name, createdTime = created, lastModifiedTime = modified,
            aspectRatio = arStr,
            videoClips = gson.fromJson(vJson, timelineType) ?: emptyList(),
            audioTracks = gson.fromJson(aJson, audioType) ?: emptyList(),
            overlays = gson.fromJson(oJson, overlayType) ?: emptyList(),
            pipTracks = gson.fromJson(pipJson, pipType) ?: emptyList(),
            beatMarkers = gson.fromJson(bmJson, beatType) ?: emptyList(),
            originalVideoVolume = origVol,
            backgroundMusicVolume = bgVol,
            audioDuckingEnabled = duckEnabled,
            audioDuckLevel = duckLevel
        )
    }

    private fun queryAllProjects(): List<ProjectEntity> {
        val list = mutableListOf<ProjectEntity>()
        readableDatabase.rawQuery(
            "SELECT * FROM projects ORDER BY lastModifiedTime DESC", null
        ).use { c -> while (c.moveToNext()) list.add(rowToEntity(c)) }
        return list
    }

    private val daoImpl = object : ProjectDao {
        override fun getAllProjectsFlow(): Flow<List<ProjectEntity>> = _projectsFlow.asStateFlow()

        override suspend fun getProjectById(id: String): ProjectEntity? =
            withContext(Dispatchers.IO) {
                readableDatabase.rawQuery(
                    "SELECT * FROM projects WHERE id = ? LIMIT 1", arrayOf(id)
                ).use { c -> if (c.moveToFirst()) rowToEntity(c) else null }
            }

        override suspend fun insertProject(project: ProjectEntity) =
            withContext(Dispatchers.IO) {
                val v = ContentValues().apply {
                    put("id", project.id)
                    put("name", project.name)
                    put("createdTime", project.createdTime)
                    put("lastModifiedTime", project.lastModifiedTime)
                    put("aspectRatio", project.aspectRatio)
                    put("videoClips", gson.toJson(project.videoClips))
                    put("audioTracks", gson.toJson(project.audioTracks))
                    put("overlays", gson.toJson(project.overlays))
                    put("pipTracks", gson.toJson(project.pipTracks))
                    put("beatMarkers", gson.toJson(project.beatMarkers))
                    put("originalVideoVolume", project.originalVideoVolume)
                    put("backgroundMusicVolume", project.backgroundMusicVolume)
                    put("audioDuckingEnabled", if (project.audioDuckingEnabled) 1 else 0)
                    put("audioDuckLevel", project.audioDuckLevel)
                }
                writableDatabase.insertWithOnConflict(
                    "projects", null, v, SQLiteDatabase.CONFLICT_REPLACE
                )
                refreshFlow()
            }

        override suspend fun updateProject(project: ProjectEntity) = insertProject(project)

        override suspend fun deleteProjectById(id: String) =
            withContext(Dispatchers.IO) {
                writableDatabase.delete("projects", "id = ?", arrayOf(id))
                refreshFlow()
            }
    }

    fun projectDao(): ProjectDao = daoImpl

    companion object {
        @Volatile private var INSTANCE: ClipCutDatabase? = null
        fun getInstance(context: Context): ClipCutDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClipCutDatabase(context.applicationContext).also { INSTANCE = it }
            }
    }
}
