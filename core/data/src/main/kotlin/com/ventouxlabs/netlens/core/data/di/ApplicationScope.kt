package com.ventouxlabs.netlens.core.data.di

import javax.inject.Qualifier

/**
 * Marks a `CoroutineScope` that lives as long as the process.
 *
 * For writes that must outlive the component which started them — closing a database row from a
 * ViewModel's `onCleared`, say, where `viewModelScope` has already been cancelled and a `launch`
 * on it would be dropped silently.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
