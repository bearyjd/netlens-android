package com.ventouxlabs.netlens.core.data.di

import javax.inject.Qualifier

/**
 * Marks the CPU-bound dispatcher (`Dispatchers.Default`).
 *
 * Injected rather than hardcoded so a test can pass a `TestDispatcher` and keep its assertions
 * deterministic instead of racing a real thread pool.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
