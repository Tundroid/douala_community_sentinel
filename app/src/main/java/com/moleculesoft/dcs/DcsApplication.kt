package com.moleculesoft.dcs

import android.app.Application
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.auth.Auth
import androidx.room.Room
import com.moleculesoft.dcs.data.local.AppDatabase
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

class DcsApplication : Application() {
    companion object {
        lateinit var instance: DcsApplication
            private set
        lateinit var supabase: SupabaseClient
        lateinit var database: AppDatabase
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "dcs_database",
        ).fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        
        supabase = createSupabaseClient(
            supabaseUrl = "https://jsgafguvblfjgaktrmqs.supabase.co",
            supabaseKey = "sb_publishable_yF_cXcUa9kywfCH3BzH3jw_b55I4PZz"
        ) {
            install(Postgrest)
            install(Auth) {
                autoLoadFromStorage = true
            }
            install(Storage)

            defaultSerializer = KotlinXSerializer(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }
}
