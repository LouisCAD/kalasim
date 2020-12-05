package org.kalasim.misc

import org.koin.core.context.KoinContext

 /*
 * Copyright 2017-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.core.error.KoinAppAlreadyStartedException
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/**
 * Global context - current Koin Application available globally
 *
 * Support to help inject automatically instances once KoinApp has been started
 *
 * @author Arnaud Giuliani
 */
class CustomContext : KoinContext {

    private var _koin: Koin? = null

    override fun get(): Koin = _koin ?: error("KoinApplication has not been started")

    override fun getOrNull(): Koin? = _koin

    override fun register(koinApplication: KoinApplication) {
        if (_koin != null) {
            throw KoinAppAlreadyStartedException("A Koin Application has already been started")
        }
        _koin = koinApplication.koin
    }

    /**
     * Start a Koin Application as StandAlone
     */
     fun startKoin(koinContext: KoinContext,
                           appDeclaration: KoinAppDeclaration): KoinApplication = synchronized(this) {
        val koinApplication = KoinApplication.init()
        koinContext.register(koinApplication)
        appDeclaration(koinApplication)
        koinApplication.createEagerInstances()
        return koinApplication
    }


    override fun stop() = synchronized(this) {
        _koin?.close()
        _koin = null
    }

    /**
     * load Koin module in global Koin context
     */
    internal fun loadKoinModules(module: Module) = synchronized(this) {
        get().loadModules(listOf(module))
    }

    /**
     * load Koin modules in global Koin context
     */
    internal fun loadKoinModules(modules: List<Module>) = synchronized(this) {
        get().loadModules(modules)
    }

    /**
     * unload Koin modules from global Koin context
     */
    internal fun unloadKoinModules(module: Module) = synchronized(this) {
        get().unloadModules(listOf(module))
    }

    /**
     * unload Koin modules from global Koin context
     */
    internal fun unloadKoinModules(modules: List<Module>) = synchronized(this) {
        get().unloadModules(modules)
    }
}
