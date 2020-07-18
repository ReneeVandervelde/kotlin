/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.resolve.dfa.symbol
import org.jetbrains.kotlin.fir.resolve.firSymbolProvider
import org.jetbrains.kotlin.fir.symbols.AbstractFirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.CallableId
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousObjectSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class FirEffectiveVisibilityResolverImpl(private val session: FirSession) : FirEffectiveVisibilityResolver {
    val cache = mutableMapOf<FirSourceElement, FirEffectiveVisibility>()

    private fun FirElement.remember(effectiveVisibility: FirEffectiveVisibility): FirEffectiveVisibility {
        val source = source
        if (source != null) {
            cache[source] = effectiveVisibility
        }
        return effectiveVisibility
    }

    override fun resolveFor(declaration: FirMemberDeclaration, containingDeclarations: List<FirDeclaration>?): FirEffectiveVisibility {
        if (declaration.source != null) {
            cache[declaration.source]?.let {
                return it
            }
        }

        val (parentSymbol, parentEffectiveVisibility) = declaration.getParentInfo(containingDeclarations)
        var selfEffectiveVisibility = declaration.visibility.firEffectiveVisibility(session, parentSymbol)
        selfEffectiveVisibility = parentEffectiveVisibility.lowerBound(selfEffectiveVisibility)
        return declaration.remember(selfEffectiveVisibility)
    }

    private fun FirMemberDeclaration.getParentInfo(containingDeclarations: List<FirDeclaration>?): Pair<FirClassLikeSymbol<*>?, FirEffectiveVisibility> {
        var parentEffectiveVisibility: FirEffectiveVisibility = FirEffectiveVisibilityImpl.Public
        // because for now effective visibility
        // only works with FirClassLikeSymbol's
        var parentSymbol: FirClassLikeSymbol<*>? = null
        val parentClassId = this.getParentClassId()

        // for some reason ClassId for "/<anonymous>"
        // has local = false but still returns
        // null instead of a symbol
        // TODO: fix
        var succeededToGetSymbol = false

        if (parentClassId?.isLocal == false) {
            parentSymbol = session.firSymbolProvider.getClassLikeSymbolByFqName(parentClassId)
            parentSymbol?.fir.safeAs<FirMemberDeclaration>()?.let {
                succeededToGetSymbol = true
                parentEffectiveVisibility = resolveFor(it, null)
            }
        }

        // we suppose we can't ever get a local classId
        // of some fir from outside of our containing declarations
        if (!succeededToGetSymbol && parentClassId != null && containingDeclarations != null) {
            parentClassId.findOuterContainerInfo(containingDeclarations)?.let {
                parentSymbol = it.first
                parentEffectiveVisibility = it.second
            }
        }

        return parentSymbol to parentEffectiveVisibility
    }

    private fun FirDeclaration.getParentClassId(): ClassId? = when (this) {
        is FirCallableMemberDeclaration<*> -> this.symbol.callableId.classId
        is FirClassLikeDeclaration<*> -> this.symbol.classId.outerClassId
        else -> null
    }

    private fun FirDeclaration.getClassId(): ClassId? = when (this) {
        is FirCallableMemberDeclaration<*> -> this.symbol.callableId.classId
        is FirClassLikeDeclaration<*> -> this.symbol.classId
        else -> null
    }

    private fun ClassId.findOuterContainerInfo(
        containingDeclarations: List<FirDeclaration>
    ): Pair<FirClassLikeSymbol<*>, FirEffectiveVisibility>? {
        for (index in containingDeclarations.indices) {
            val declaration = containingDeclarations[index]
            val declarationClassId = declaration.getClassId()

            // because classId's we take from firs are
            // not the same instances we find in containingDeclarations
            // TODO: fix
            if (this.asSingleFqName() == declarationClassId?.asSingleFqName()) {
                return when (declaration) {
                    is FirRegularClass -> {
                        declaration.symbol to resolveFor(declaration, containingDeclarations.subList(0, index))
                    }
                    is FirAnonymousObject -> {
                        declaration.symbol to declaration.remember(FirEffectiveVisibilityImpl.Local)
                    }
                    else -> {
                        null
                    }
                }
            }
        }

        return null
    }
}