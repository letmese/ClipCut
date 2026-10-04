package com.clipcut.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.AudioTrackItem
import com.clipcut.core.model.OverlayItem
import com.clipcut.core.model.TimelineItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ClipCutDatabase private constructor(context: Context) : SQLiteOpenHelper(context, "clipcut.db", null, 1) {

    private val gson = Gson()
    private val _projectsFlow = MutableStateFlow<List<ProjectEntity>>(emptyList())

    init {
        refreshFlow()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS projects (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                createdTime INTEGER NOT NULL,
                lastModifiedTime INTEGER NOT NULL,
                videoClips TEXT NOT NULL,
                audioTracks TEXT NOT NULL,
                overlays TEXT NOT NULL,
                originalVideoVolume REAL NOT NULL,
                backgroundMusicVolume REAL NOT NULL
            )
        """.trimIndent())
        AppLog.i("ClipCutDatabase", "SQLite projects table created")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS projects")
        onCreate(db)
    }

    private fun refreshFlow() {
        try {
            val list = queryAllProjects()
            _projectsFlow.value = list
        } catch (e: Exception) {
            AppLog.e("ClipCutDatabase", "Error refreshing projects flow", e)
        }
    }

    private fun queryAllProjects(): List<ProjectEntity> {
        val list = mutableListOf<ProjectEntity>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM projects ORDER BY lastModifiedTime DESC", null)
        cursor.use {
            val timelineType = object : TypeToken<List<TimelineItem>>() {}.type
            val audioType = object : TypeToken<List<AudioTrackItem>>() {}.type
            val overlayType = object : TypeToken<List<OverlayItem>>() {}.type

            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndexOrThrow("id"))
                val name = it.getString(it.getColumnIndexOrThrow("name"))
                val createdTime = it.getLong(it.getColumnIndexOrThrow("createdTime"))
                val lastModifiedTime = it.getLong(it.getColumnIndexOrThrow("lastModifiedTime"))
                val videoClipsJson = it.getString(it.getColumnIndexOrThrow("videoClips"))
                val audioTracksJson = it.getString(it.getColumnIndexOrThrow("audioTracks"))
                val overlaysJson = it.getString(it.getColumnIndexOrThrow("overlays"))
                val origVol = it.getFloat(it.getColumnIndexOrThrow("originalVideoVolume"))
                val bgVol = it.getFloat(it.getColumnIndexOrThrow("backgroundMusicVolume"))

                val videoClips: List<TimelineItem> = gson.fromJson(videoClipsJson, timelineType) ?: emptyList()
                val audioTracks: List<AudioTrackItem> = gson.fromJson(audioTracksJson, audioType) ?: emptyList()
                val overlays: List<OverlayItem> = gson.fromJson(overlaysJson, overlayType) ?: emptyList()

                list.add(
                    ProjectEntity(
                        id = id,
                        name = name,
                        createdTime = createdTime,
                        lastModifiedTime = lastModifiedTime,
                        videoClips = videoClips,
                        audioTracks = audioTracks,
                        overlays = overlays,
                        originalVideoVolume = origVol,
                        backgroundMusicVolume = bgVol
                    )
                )
            }
        }
        return list
    }

    private val daoImpl = object : ProjectDao {
        override fun getAllProjectsFlow(): Flow<List<ProjectEntity>> = _projectsFlow.asStateFlow()

        override suspend fun getProjectById(id: String): ProjectEntity? = withContext(Dispatchers.IO) {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM projects WHERE id = ? LIMIT 1", arrayOf(id))
            cursor.use {
                if (it.moveToFirst()) {
                    val timelineType = object : TypeToken<List<TimelineItem>>() {}.type
                    val audioType = object : TypeToken<List<AudioTrackItem>>() {}.type
                    val overlayType = object : TypeToken<List<OverlayItem>>() {}.type

                    val name = it.getString(it.getColumnIndexOrThrow("name"))
                    val createdTime = it.getLong(it.getColumnIndexOrThrow("createdTime"))
                    val lastModifiedTime = it.getLong(it.getColumnIndexOrThrow("lastModifiedTime"))
                    val videoClipsJson = it.getString(it.getColumnIndexOrThrow("videoClips"))
                    val audioTracksJson = it.getString(it.getColumnIndexOrThrow("audioTracks"))
                    val overlaysJson = it.getString(it.getColumnIndexOrThrow("overlays"))
                    val origVol = it.getFloat(it.getColumnIndexOrThrow("originalVideoVolume"))
                    val bgVol = it.getFloat(it.getColumnIndexOrThrow("backgroundMusicVolume"))

                    val videoClips: List<TimelineItem> = gson.fromJson(videoClipsJson, timelineType) ?: emptyList()
                    val audioTracks: List<AudioTrackItem> = gson.fromJson(audioTracksJson, audioType) ?: emptyList()
                    val overlays: List<OverlayItem> = gson.fromJson(overlaysJson, overlayType) ?: emptyList()

                    ProjectEntity(
                        id = id,
                        name = name,
                        createdTime = createdTime,
                        lastModifiedTime = lastModifiedTime,
                        videoClips = videoClips,
                        audioTracks = audioTracks,
                        overlays = overlays,
                        originalVideoVolume = origVol,
                        backgroundMusicVolume = bgVol
                    )
                } else null
            }
        }

        override suspend fun insertProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("id", project.id)
                put("name", project.name)
                put("createdTime", project.createdTime)
                put("lastModifiedTime", project.lastModifiedTime)
                put("videoClips", gson.toJson(project.videoClips))
                put("audioTracks", gson.toJson(project.audioTracks))
                put("overlays", gson.toJson(project.overlays))
                put("originalVideoVolume", project.originalVideoVolume)
                put("backgroundMusicVolume", project.backgroundMusicVolume)
            }
            db.insertWithOnConflict("projects", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            refreshFlow()
        }

        override suspend fun updateProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
            insertProject(project)
        }

        override suspend fun deleteProjectById(id: String) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.delete("projects", "id = ?", arrayOf(id))
            refreshFlow()
        }
    }

    fun projectDao(): ProjectDao = daoImpl

    companion object {
        @Volatile
        private var INSTANCE: ClipCutDatabase? = null

        fun getInstance(context: Context): ClipCutDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClipCutDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
