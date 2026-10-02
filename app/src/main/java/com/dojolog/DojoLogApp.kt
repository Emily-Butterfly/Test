package com.dojolog

import android.app.Application
import com.dojolog.data.TrainingRepository
import com.dojolog.data.db.AppDatabase

class DojoLogApp : Application() {
    val repository: TrainingRepository by lazy { TrainingRepository(AppDatabase.create(this)) }
}
