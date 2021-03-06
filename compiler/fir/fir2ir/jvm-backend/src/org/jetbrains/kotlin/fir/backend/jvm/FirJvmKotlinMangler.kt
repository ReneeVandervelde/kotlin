/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend.jvm

import org.jetbrains.kotlin.backend.common.serialization.mangle.*
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.signaturer.FirMangler

class FirJvmKotlinMangler(private val session: FirSession) : AbstractKotlinMangler<FirDeclaration>(), FirMangler {

    override val FirDeclaration.mangleString: String
        get() = getMangleComputer(MangleMode.FULL).computeMangle(this)

    override val FirDeclaration.signatureString: String
        get() = getMangleComputer(MangleMode.SIGNATURE).computeMangle(this)

    override val FirDeclaration.fqnString: String
        get() = getMangleComputer(MangleMode.FQNAME).computeMangle(this)

    override fun FirDeclaration.isExported(): Boolean = true

    override fun getExportChecker(): KotlinExportChecker<FirDeclaration> {
        return object : KotlinExportChecker<FirDeclaration> {
            override fun check(declaration: FirDeclaration, type: SpecialDeclarationType): Boolean = true

            override fun FirDeclaration.isPlatformSpecificExported(): Boolean = true
        }
    }

    override fun getMangleComputer(mode: MangleMode): KotlinMangleComputer<FirDeclaration> {
        return FirJvmMangleComputer(StringBuilder(256), mode, session)
    }
}