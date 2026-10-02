package com.dojolog.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.dojolog.DojoLogApp
import com.dojolog.data.TrainingRepository

/** The app-wide repository, for use inside `viewModelFactory { initializer { ... } }`. */
val CreationExtras.repository: TrainingRepository
    get() = (checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as DojoLogApp).repository
