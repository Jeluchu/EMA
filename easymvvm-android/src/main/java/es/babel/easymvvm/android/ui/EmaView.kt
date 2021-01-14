package es.babel.easymvvm.android.ui

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import es.babel.easymvvm.android.extra.EmaReceiverModel
import es.babel.easymvvm.android.extra.EmaResultModel
import es.babel.easymvvm.android.viewmodel.EmaFactory
import es.babel.easymvvm.android.viewmodel.EmaViewModel
import es.babel.easymvvm.core.navigator.EmaNavigationState
import es.babel.easymvvm.core.navigator.EmaBaseNavigator
import es.babel.easymvvm.core.state.EmaBaseState
import es.babel.easymvvm.core.state.EmaExtraData
import es.babel.easymvvm.core.state.EmaState
import java.lang.Exception
import kotlin.jvm.internal.PropertyReference0
import kotlin.reflect.KProperty


/**
 * View to handle VM view logic states through [EmaState].
 * The user must provide in the constructor by template:
 *  - The view model class [EmaViewModel] is going to use the view
 *  - The navigation state class [EmaNavigationState] will handle the navigation
 *
 * @author <a href="mailto:apps.carmabs@gmail.com">Carlos Mateo Benito</a>
 */
interface EmaView<S : EmaBaseState, VM : EmaViewModel<S, NS>, NS : EmaNavigationState> {

    /**
     * The view mdeol seed [EmaViewModel] for the view
     */
    val viewModelSeed: VM

    /**
     * The navigator [EmaBaseNavigator]
     */
    val navigator: EmaBaseNavigator<NS>?

    /**
     * The state set up form previous views when it is launched.
     */
    val inputState: S?

    /**
     *
     */
    var previousState: S?

    /**
     * Create the view model for the view and create 3 observers
     *  - View state observer. Called as well when view is attached to the view model
     *  - Single event observer. Not called when the view is first time attached to the view model
     *  - Navigation observer. Not called when the view is first time attached to the view model
     *
     * This observers handle the events generated by view updates/errors/navigation
     * @param fragmentActivity the scope of the view model
     * @param fragment the scope of view model. If it is provided it will be the scope of the view model
     */
    fun initializeViewModel(fragmentActivity: FragmentActivity, fragment: Fragment? = null) {
        val emaFactory = EmaFactory(viewModelSeed)
        val vm = fragment?.let {
            ViewModelProviders.of(it, emaFactory)[viewModelSeed::class.java]
        } ?: ViewModelProviders.of(fragmentActivity, emaFactory)[viewModelSeed::class.java]

        val resultViewModel = ViewModelProviders.of(fragmentActivity)[EmaResultViewModel::class.java]
        vm.resultViewModel = resultViewModel

        onViewModelInitialized(vm)

        vm.onStart(inputState?.let { EmaState.Normal(it) })
        vm.state?.data?.let {
            onStateNormalFirstTime(it)
        }
        vm.observableState.observe(fragment ?: fragmentActivity, Observer(this::onDataUpdated))
        vm.singleObservableState.observe(fragment
                ?: fragmentActivity, Observer(this::onSingleData))
        vm.navigationState.observe(fragment
                ?: fragmentActivity, Observer(this::onNavigation))

        resultViewModel.resultEvent.observe(fragment
                ?: fragmentActivity, Observer(this::onResultSetHandled))
        resultViewModel.resultReceiverEvent.observe(fragment
                ?: fragmentActivity, Observer(this::onResultReceivedHandled))
    }

    /**
     * Called when view model trigger an update view event
     * @param state of the view
     */
    fun onDataUpdated(state: EmaState<S>) {
        onStateNormal(state.data)
        when (state) {
            is EmaState.Alternative -> {
                onStateAlternative(state.dataAlternative)
            }
            is EmaState.Error -> {
                onStateError(state.error)
            }
        }

        previousState = state.data
    }

    /**
     * Check EMA state selected property to execute action with new value if it has changed
     * @param action Action to execute. Current value passed in lambda.
     * @param field Ema State field to check if it has been changed.
     * @param areEqualComparator Comparator to determine if both objects are equals. Useful for complex objects
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> bindForUpdate(field: KProperty<T>, areEqualComparator: ((previous: T?, current: T?) -> Boolean)? = null, action: (current: T?) -> Unit) {
        val currentClass = (field as PropertyReference0).boundReceiver as? S
        currentClass?.also { _ ->
            val currentValue = field.get() as T
            previousState?.also {
                try {
                    val previousField = it.javaClass.getDeclaredField(field.name)
                    previousField.isAccessible = true
                    val previousValue = previousField.get(previousState) as T
                    if (areEqualComparator?.invoke(previousValue, currentValue)?.not()
                                    ?: (previousValue != currentValue)) {
                        action.invoke(currentValue)
                    }
                } catch (e: Exception) {
                    Log.d("EMA", "Field not found")
                }
            } ?: action.invoke(currentValue)
        } ?: Log.d("EMA", "Bounding class must be the state of the view")
    }

    /**
     * Check EMA state selected property to execute action with new value if it has changed
     * @param action Action to execute. Current and previous value passed in lambda
     * @param field Ema State field to check if it has been changed
     * @param areEqualComparator Comparator to determine if both objects are equals. Useful for complex objects
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> bindForUpdateWithPrevious(field: KProperty<T>, areEqualComparator: ((previous: T?, current: T?) -> Boolean)? = null, action: (previous: T?, current: T?) -> Unit) {
        val currentClass = (field as PropertyReference0).boundReceiver as? S
        currentClass?.also { _ ->
            val currentValue = field.get() as T
            previousState?.also {
                try {
                    val previousField = it.javaClass.getDeclaredField(field.name)
                    previousField.isAccessible = true
                    val previousValue = previousField.get(previousState) as T
                    if (areEqualComparator?.invoke(previousValue, currentValue)?.not()
                                    ?: (previousValue != currentValue)) {
                        action.invoke(previousValue,currentValue)
                    }
                } catch (e: Exception) {
                    Log.d("EMA", "Field not found")
                }
            } ?: action.invoke(null,currentValue)
        } ?: Log.d("EMA", "Bounding class must be the state of the view")
    }

    /**
     * Called when view model trigger a result event
     * @param result model
     */
    fun onResultSetHandled(result: EmaResultModel) {
        onResultSetEvent(result)
    }

    /**
     * Called when view model invoke a result receiver event
     * @param receiver model
     */
    fun onResultReceivedHandled(receiver: EmaReceiverModel) {
        onResultReceiverInvokeEvent(receiver)
    }

    /**
     * Called when view model trigger an only once notified event
     * @param data for extra information
     */
    fun onSingleData(data: EmaExtraData) {
        onSingleEvent(data)
    }

    /**
     * Called when view model trigger an only once notified event for navigation
     * @param navigation state with information about the destination
     */
    fun onNavigation(navigation: EmaNavigationState?) {
        navigation?.let {
            navigate(navigation)
        } ?: navigateBack()
    }

    /**
     * Called once the view model have been provided. Here must go the view set up
     * @param viewModel of the view
     */
    fun onViewModelInitialized(viewModel: VM)

    /**
     * Called when view model trigger an update view event
     * @param data with the state of the view
     */
    fun onStateNormal(data: S)

    /**
     * Called when view model is loaded first time
     * @param data with the state of the view
     */
    fun onStateNormalFirstTime(data: S)

    /**
     * Called when view model trigger a updateToAlternativeState event
     * @param data with information about updateToAlternativeState
     */
    fun onStateAlternative(data: EmaExtraData)

    /**
     * Called when view model trigger an only once notified event.Not called when the view is first time attached to the view model
     * @param data with information about updateToAlternativeState
     */
    fun onSingleEvent(data: EmaExtraData)

    /**
     * Called when view model trigger an error event
     * @param error generated by view model
     */
    fun onStateError(error: Throwable)

    /**
     * Called when a result has been notified from view model
     * @param emaResultModel generated by view model
     */
    fun onResultSetEvent(emaResultModel: EmaResultModel)

    /**
     * Called when a result receiver has been invoked from view model
     * @param emaReceiverModel generated by view model
     */
    fun onResultReceiverInvokeEvent(emaReceiverModel: EmaReceiverModel)

    /**
     * Called when view model trigger a navigation event
     * @param state with info about destination
     */
    @Suppress("UNCHECKED_CAST")
    fun navigate(state: EmaNavigationState) {
        navigator?.navigate(state as NS)
    }

    /**
     * Called when view model trigger a navigation back event
     * @return True
     */
    fun navigateBack():Boolean {
        return navigator?.navigateBack()?:false
    }


}