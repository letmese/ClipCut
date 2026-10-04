package com.clipcut.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.AudioTrackItem
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
    SQLiteOpenHelper(context, "clipcut.db", null, 2) {

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
                videoClips TEXT NOT NULL,
                audioTracks TEXT NOT NULL,
                overlays TEXT NOT NULL,
                pipTracks TEXT NOT NULL DEFAULT '[]',
                originalVideoVolume REAL NOT NULL,
                backgroundMusicVolume REAL NOT NULL
            )
        """.trimIndent())
        AppLog.i("ClipCutDatabase", "SQLite projects table created (v2)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        AppLog.w("ClipCutDatabase", "DB upgrade $oldVersion -> $newVersion")
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE projects ADD COLUMN pipTracks TEXT NOT NULL DEFAULT '[]'")
                AppLog.i("ClipCutDatabase", "Added pipTracks column to projects table")
                return
            } catch (e: Exception) {
                AppLog.e("ClipCutDatabase", "ALTER TABLE failed, recreating table", e)
            }
        }
        db.execSQL("DROP TABLE IF EXISTS projects")
        onCreate(db)
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

        val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
        val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
        val created = cursor.getLong(cursor.getColumnIndexOrThrow("createdTime"))
        val modified = cursor.getLong(cursor.getColumnIndexOrThrow("lastModifiedTime"))
        val vJson = cursor.getString(cursor.getColumnIndexOrThrow("videoClips"))
        val aJson = cursor.getString(cursor.getColumnIndexOrThrow("audioTracks"))
        val oJson = cursor.getString(cursor.getColumnIndexOrThrow("overlays"))
        val pipIdx = cursor.getColumnIndex("pipTracks")
        val pipJson = if (pipIdx >= 0) cursor.getString(pipIdx) else "[]"
        val origVol = cursor.getFloat(cursor.getColumnIndexOrThrow("originalVideoVolume"))
        val bgVol = cursor.getFloat(cursor.getColumnIndexOrThrow("backgroundMusicVolume"))

        return ProjectEntity(
            id = id, name = name, createdTime = created, lastModifiedTime = modified,
            videoClips = gson.fromJson(vJson, timelineType) ?: emptyList(),
            audioTracks = gson.fromJson(aJson, audioType) ?: emptyList(),
            overlays = gson.fromJson(oJson, overlayType) ?: emptyList(),
            pipTracks = gson.fromJson(pipJson, pipType) ?: emptyList(),
            originalVideoVolume = origVol,
            backgroundMusicVolume = bgVol
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
                    put("videoClips", gson.toJson(project.videoClips))
                    put("audioTracks", gson.toJson(project.audioTracks))
                    put("overlays", gson.toJson(project.overlays))
                    put("pipTracks", gson.toJson(project.pipTracks))
                    put("originalVideoVolume", project.originalVideoVolume)
                    put("backgroundMusicVolume", project.backgroundMusicVolume)
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
