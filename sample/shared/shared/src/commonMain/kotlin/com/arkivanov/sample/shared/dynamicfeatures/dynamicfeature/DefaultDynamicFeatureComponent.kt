package com.arkivanov.sample.shared.dynamicfeatures.dynamicfeature

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceCurrent
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.subscribe
import com.arkivanov.sample.shared.dynamicfeatures.dynamicfeature.DynamicFeatureComponent.Child
import com.arkivanov.sample.shared.dynamicfeatures.dynamicfeature.DynamicFeatureComponent.Child.ErrorChild
import com.arkivanov.sample.shared.dynamicfeatures.dynamicfeature.DynamicFeatureComponent.Child.FeatureChild
import com.arkivanov.sample.shared.dynamicfeatures.dynamicfeature.DynamicFeatureComponent.Child.LoadingChild
import com.badoo.reaktive.disposable.scope.DisposableScope
import com.badoo.reaktive.disposable.scope.disposableScope
import kotlinx.serialization.Serializable

internal class DefaultDynamicFeatureComponent<out T : Any>(
    componentContext: ComponentContext,
    private val name: String,
    private val featureInstaller: FeatureInstaller,
    private val factory: (ComponentContext) -> T
) : DynamicFeatureComponent<T>, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    private val stack =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config.LOADING,
            childFactory = ::child,
        )

    override val childStack: Value<ChildStack<*, Child<T>>> get() = stack

    private fun child(config: Config, componentContext: ComponentContext): Child<T> =
        when (config) {
            Config.LOADING -> loading(componentContext)
            Config.FEATURE -> FeatureChild(factory(componentContext))
            Config.ERROR -> ErrorChild(name = name)
        }

    private fun loading(componentContext: ComponentContext): LoadingChild {
        disposableScope {
            componentContext.lifecycle.subscribe(
                onCreate = { loadFeature() },
                onDestroy = ::dispose
            )
        }

        return LoadingChild(name = name)
    }

    private fun DisposableScope.loadFeature() {
        featureInstaller.install(name = name).subscribeScoped {
            when (it) {
                is FeatureInstaller.Result.Installed -> navigation.replaceCurrent(Config.FEATURE)
                is FeatureInstaller.Result.Cancelled,
                is FeatureInstaller.Result.Error -> navigation.replaceCurrent(Config.ERROR)
            }.let {}
        }
    }

    @Serializable
    private enum class Config {
        LOADING,
        FEATURE,
        ERROR,
    }
}
