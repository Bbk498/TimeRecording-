package com.example.timerecording.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.timerecording.data.entity.ProjectEntity
import com.example.timerecording.data.entity.SessionEntity

/**
 * Room 数据库定义，应用的本地数据库入口。
 * 包含 projects 和 sessions 两张表，通过单例模式提供全局唯一实例。
 * 数据库文件名为 timerecording.db。
 */
@Database(
    entities = [ProjectEntity::class, SessionEntity::class],  // 注册所有实体表
    version = 1,                                                // 数据库版本号
    exportSchema = false                                         // 不导出 schema JSON 文件
)
abstract class AppDatabase : RoomDatabase() {

    /** 获取项目表的 DAO（数据访问对象） */
    abstract fun projectDao(): ProjectDao

    /** 获取会话表的 DAO（数据访问对象） */
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null  // 单例实例，@Volatile 保证多线程可见性

        /**
         * 获取数据库单例。
         * 使用双重检查锁（double-checked locking）确保线程安全地创建唯一实例。
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "timerecording.db"        // 数据库文件名
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
