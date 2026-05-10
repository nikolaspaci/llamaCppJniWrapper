package com.nikolaspaci.app.llamallmlocal.di

import android.content.Context
import android.content.SharedPreferences
import com.nikolaspaci.app.llamallmlocal.data.database.AppDatabase
import com.nikolaspaci.app.llamallmlocal.data.database.ChatDao
import com.nikolaspaci.app.llamallmlocal.data.database.ModelDao
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameterDao
import com.nikolaspaci.app.llamallmlocal.data.database.SystemPromptPresetDao
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository
import com.nikolaspaci.app.llamallmlocal.data.repository.ModelParameterRepository
import com.nikolaspaci.app.llamallmlocal.data.repository.ModelRepository
import com.nikolaspaci.app.llamallmlocal.data.repository.SystemPromptPresetRepository
import com.nikolaspaci.app.llamallmlocal.engine.DefaultModelParameterProvider
import com.nikolaspaci.app.llamallmlocal.engine.LlamaEngine
import com.nikolaspaci.app.llamallmlocal.engine.ModelEngine
import com.nikolaspaci.app.llamallmlocal.engine.ModelParameterProvider
import com.nikolaspaci.app.llamallmlocal.data.curated.AppVersionCode
import com.nikolaspaci.app.llamallmlocal.util.HardwareCapabilities
import com.nikolaspaci.app.llamallmlocal.util.OptimalConfigurationService
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    fun provideModelParameterDao(database: AppDatabase): ModelParameterDao {
        return database.modelParameterDao()
    }

    @Provides
    fun provideModelDao(database: AppDatabase): ModelDao {
        return database.modelDao()
    }

    @Provides
    @Singleton
    fun provideChatRepository(chatDao: ChatDao): ChatRepository {
        return ChatRepository(chatDao)
    }

    @Provides
    @Singleton
    fun provideModelParameterRepository(dao: ModelParameterDao): ModelParameterRepository {
        return ModelParameterRepository(dao)
    }

    @Provides
    @Singleton
    fun provideModelRepository(dao: ModelDao): ModelRepository {
        return ModelRepository(dao)
    }

    @Provides
    fun provideSystemPromptPresetDao(database: AppDatabase): SystemPromptPresetDao {
        return database.systemPromptPresetDao()
    }

    @Provides
    @Singleton
    fun provideSystemPromptPresetRepository(dao: SystemPromptPresetDao): SystemPromptPresetRepository {
        return SystemPromptPresetRepository(dao)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("llama_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideHardwareCapabilities(): HardwareCapabilities {
        return HardwareCapabilities()
    }

    @Provides
    @Singleton
    fun provideOptimalConfigurationService(
        @ApplicationContext context: Context,
        hardwareCapabilities: HardwareCapabilities
    ): OptimalConfigurationService {
        return OptimalConfigurationService(context, hardwareCapabilities)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        // Single source of truth for Firestore configuration. Hilt instantiates
        // this @Singleton on first injection and never again, so the settings
        // assignment happens exactly once and before any Firestore operation —
        // provided every consumer gets the instance through Hilt and nobody
        // touches Firebase.firestore directly.
        return Firebase.firestore.apply {
            firestoreSettings = firestoreSettings {
                setLocalCacheSettings(persistentCacheSettings { })
            }
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(): FirebaseCrashlytics = Firebase.crashlytics

    @Provides
    @Singleton
    fun provideAppVersionCode(@ApplicationContext context: Context): AppVersionCode {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return AppVersionCode(info.longVersionCode)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @Singleton
    abstract fun bindModelEngine(engine: LlamaEngine): ModelEngine

    @Binds
    @Singleton
    abstract fun bindModelParameterProvider(provider: DefaultModelParameterProvider): ModelParameterProvider
}
