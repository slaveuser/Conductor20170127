package com.bluelinelabs.conductor;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.bluelinelabs.conductor.Router.OnControllerPushedListener;
import com.bluelinelabs.conductor.internal.ClassUtils;
import com.bluelinelabs.conductor.internal.RouterRequiringFunc;
import com.bluelinelabs.conductor.internal.ViewAttachHandler;
import com.bluelinelabs.conductor.internal.ViewAttachHandler.ViewAttachListener;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * A Controller manages portions of the UI. It is similar to an Activity or Fragment in that it manages its
 * own lifecycle and controls interactions between the UI and whatever logic is required. It is, however,
 * a much lighter weight component than either Activities or Fragments. While it offers several lifecycle
 * methods, they are much simpler and more predictable than those of Activities and Fragments.
 */
public abstract class Controller {

    private static final String KEY_CLASS_NAME = "Controller.className";
    private static final String KEY_VIEW_STATE = "Controller.viewState";
    private static final String KEY_CHILD_ROUTERS = "Controller.childRouters";
    private static final String KEY_CHILD_BACKSTACK = "Controller.childBackstack";
    private static final String KEY_SAVED_STATE = "Controller.savedState";
    private static final String KEY_INSTANCE_ID = "Controller.instanceId";
    private static final String KEY_TARGET_INSTANCE_ID = "Controller.target.instanceId";
    private static final String KEY_ARGS = "Controller.args";
    private static final String KEY_NEEDS_ATTACH = "Controller.needsAttach";
    private static final String KEY_REQUESTED_PERMISSIONS = "Controller.requestedPermissions";
    private static final String KEY_OVERRIDDEN_PUSH_HANDLER = "Controller.overriddenPushHandler";
    private static final String KEY_OVERRIDDEN_POP_HANDLER = "Controller.overriddenPopHandler";
    private static final String KEY_VIEW_STATE_HIERARCHY = "Controller.viewState.hierarchy";
    private static final String KEY_VIEW_STATE_BUNDLE = "Controller.viewState.bundle";
    private static final String KEY_RETAIN_VIEW_MODE = "Controller.retainViewMode";

    private final Bundle args;

    Bundle viewState;
    private Bundle savedInstanceState;
    private boolean isBeingDestroyed;
    private boolean destroyed;
    private boolean attached;
    private boolean hasOptionsMenu;
    private boolean optionsMenuHidden;
    private boolean viewIsAttached;
    private boolean viewWasDetached;
    private Router router;
    private View view;
    private Controller parentController;
    private String instanceId;
    private String targetInstanceId;
    private boolean needsAttach;
    private boolean attachedToUnownedParent;
    private boolean hasSavedViewState;
    private boolean isDetachFrozen;
    private ControllerChangeHandler overriddenPushHandler;
    private ControllerChangeHandler overriddenPopHandler;
    private RetainViewMode retainViewMode = RetainViewMode.RELEASE_DETACH;
    private ViewAttachHandler viewAttachHandler;
    private final List<ControllerHostedRouter> childRouters = new ArrayList<>();
    private final List<LifecycleListener> lifecycleListeners = new ArrayList<>();
    private final ArrayList<String> requestedPermissions = new ArrayList<>();
    private final ArrayList<RouterRequiringFunc> onRouterSetListeners = new ArrayList<>();
    private final List<Controller> childBackstack = new LinkedList<>();
    private WeakReference<View> destroyedView;

    private final OnControllerPushedListener onControllerPushedListener = new OnControllerPushedListener() {
        @Override
        public void onControllerPushed(Controller controller) {
            onChildControllerPushed(controller);
        }
    };

    @NonNull
    static Controller newInstance(@NonNull Bundle bundle) {
        final String className = bundle.getString(KEY_CLASS_NAME);
        //noinspection ConstantConditions
        Constructor[] constructors = ClassUtils.classForName(className, false).getConstructors();
        Constructor bundleConstructor = getBundleConstructor(constructors);

        Controller controller;
        try {
            if (bundleConstructor != null) {
                controller = (Controller)bundleConstructor.newInstance(bundle.getBundle(KEY_ARGS));
            } else {
                //noinspection ConstantConditions
                controller = (Controller)getDefaultConstructor(constructors).newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException("An exception occurred while creating a new instance of " + className + ". " + e.getMessage(), e);
        }

        controller.restoreInstanceState(bundle);
        return controller;
    }

    /**
     * Convenience constructor for use when no arguments are needed.
     */
    protected Controller() {
        this(null);
    }

    /**
     * Constructor that takes arguments that need to be retained across restarts.
     *
     * @param args Any arguments that need to be retained.
     */
    protected Controller(@Nullable Bundle args) {
        this.args = args != null ? args : new Bundle();
        instanceId = UUID.randomUUID().toString();
        ensureRequiredConstructor();
    }

    /**
     * Called when the controller is ready to display its view. A valid view must be returned. The standard body
     * for this method will be {@code return inflater.inflate(R.layout.my_layout, container, false);}, plus
     * any binding code.
     *
     * @param inflater  The LayoutInflater that should be used to inflate views
     * @param container The parent view that this Controller's view will eventually be attached to.
     *                  This Controller's view should NOT be added in this method. It is simply passed in
     *                  so that valid LayoutParams can be used during inflation.
     */
    @NonNull
    protected abstract View onCreateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container);

    /**
     * Returns the {@link Router} object that can be used for pushing or popping other Controllers
     */
    public final Router getRouter() {
        return router;
    }

    /**
     * Returns any arguments that were set in this Controller's constructor
     */
    @NonNull
    public Bundle getArgs() {
        return args;
    }

    /**
     * Retrieves the child {@link Router} for the given container. If no child router for this container
     * exists yet, it will be created.
     *
     * @param container The ViewGroup that hosts the child Router
     */
    @NonNull
    public final Router getChildRouter(@NonNull ViewGroup container) {
        return getChildRouter(container, null);
    }

    /**
     * Retrieves the child {@link Router} for the given container/tag combination. If no child router for
     * this container exists yet, it will be created. Note that multiple routers should not exist
     * in the same container unless a lot of care is taken to maintain order between them. Avoid using
     * the same container unless you have a great reason to do so (ex: ViewPagers).
     *
     * @param container The ViewGroup that hosts the child Router
     * @param tag The router's tag
     */
    @NonNull
    public final Router getChildRouter(@NonNull ViewGroup container, @Nullable String tag) {
        //noinspection ConstantConditions
        return getChildRouter(container, tag, true);
    }

    /**
     * Retrieves the child {@link Router} for the given container/tag combination. Note that multiple
     * routers should not exist in the same container unless a lot of care is taken to maintain order
     * between them. Avoid using the same container unless you have a great reason to do so (ex: ViewPagers).
     *
     * @param container The ViewGroup that hosts the child Router
     * @param tag The router's tag
     * @param createIfNeeded If true, a router will be created if one does not yet exist. Else false will be returned in this case.
     */
    @Nullable
    public final Router getChildRouter(@NonNull ViewGroup container, @Nullable String tag, boolean createIfNeeded) {
        @IdRes final int containerId = container.getId();

        ControllerHostedRouter childRouter = null;
        for (ControllerHostedRouter router : childRouters) {
            if (router.getHostId() == containerId && TextUtils.equals(tag, router.getTag())) {
                childRouter = router;
                break;
            }
        }

        if (childRouter == null) {
            if (createIfNeeded) {
                childRouter = new ControllerHostedRouter(container.getId(), tag);
                monitorChildRouter(childRouter);
                childRouter.setHost(this, container);
                childRouters.add(childRouter);
            }
        } else if (!childRouter.hasHost()) {
            childRouter.setHost(this, container);
            monitorChildRouter(childRouter);
            childRouter.rebindIfNeeded();
        }

        return childRouter;
    }

    public final void removeChildRouter(@NonNull Router childRouter) {
        if ((childRouter instanceof ControllerHostedRouter) && childRouters.remove(childRouter)) {
            childRouter.destroy(true);
        }
    }

    /**
     * Returns whether or not this Controller has been destroyed.
     */
    public final boolean isDestroyed() {
        return destroyed;
    }

    /**
     * Returns whether or not this Controller is currently in the process of being destroyed.
     */
    public final boolean isBeingDestroyed() {
        return isBeingDestroyed;
    }

    /**
     * Returns whether or not this Controller is currently attached to a host View.
     */
    public final boolean isAttached() {
        return attached;
    }

    /**
     * Return this Controller's View, if available.
     */
    @Nullable
    public final View getView() {
        return view;
    }

    /**
     * Returns the host Activity of this Controller's {@link Router}
     */
    @Nullable
    public final Activity getActivity() {
        return router != null ? router.getActivity() : null;
    }

    /**
     * Returns the Resources from the host Activity
     */
    @Nullable
    public final Resources getResources() {
        Activity activity = getActivity();
        return activity != null ? activity.getResources() : null;
    }

    /**
     * Returns the Application Context derived from the host Activity
     */
    @Nullable
    public final Context getApplicationContext() {
        Activity activity = getActivity();
        return activity != null ? activity.getApplicationContext() : null;
    }

    /**
     * Returns this Controller's parent Controller if it is a child Controller.
     */
    @Nullable
    public final Controller getParentController() {
        return parentController;
    }

    /**
     * Returns this Controller's instance ID, which is generated when the instance is created and
     * retained across restarts.
     */
    @NonNull
    public final String getInstanceId() {
        return instanceId;
    }

    /**
     * Returns the Controller with the given instance id, if available.
     * May return the controller itself or a matching descendant
     * @param instanceId The instance ID being searched for
     * @return The matching Controller, if one exists
     */
    @Nullable
    final Controller findController(@NonNull String instanceId) {
        if (this.instanceId.equals(instanceId)) {
            return this;
        }

        for (Router router : childRouters) {
            Controller matchingChild = router.getControllerWithInstanceId(instanceId);
            if (matchingChild != null) {
                return matchingChild;
            }
        }
        return null;
    }

    /**
     * Returns all of this Controller's child Routers
     */
    @NonNull
    public final List<Router> getChildRouters() {
        List<Router> routers = new ArrayList<>();
        for (Router router : childRouters) {
            routers.add(router);
        }
        return routers;
    }

    /**
     * Optional target for this Controller. One reason this could be used is to send results back to the Controller
     * that started this one. Target Controllers are retained across instances. It is recommended
     * that Controllers enforce that their target Controller conform to a specific Interface.
     *
     * @param target The Controller that is the target of this one.
     */
    public void setTargetController(@Nullable Controller target) {
        if (targetInstanceId != null) {
            throw new RuntimeException("Target controller already set. A controller's target may only be set once.");
        }

        targetInstanceId = target != null ? target.getInstanceId() : null;
    }

    /**
     * Returns the target Controller that was set with the {@link #setTargetController(Controller)} method
     *
     * @return This Controller's target
     */
    @Nullable
    public final Controller getTargetController() {
        if (targetInstanceId != null) {
            return router.getRootRouter().getControllerWithInstanceId(targetInstanceId);
        }
        return null;
    }

    /**
     * Called when this Controller's View is being destroyed. This should overridden to unbind the View
     * from any local variables.
     *
     * @param view The View to which this Controller should be bound.
     */
    protected void onDestroyView(@NonNull View view) { }

    /**
     * Called when this Controller begins the process of being swapped in or out of the host view.
     *
     * @param changeHandler The {@link ControllerChangeHandler} that's managing the swap
     * @param changeType    The type of change that's occurring
     */
    protected void onChangeStarted(@NonNull ControllerChangeHandler changeHandler, @NonNull ControllerChangeType changeType) { }

    /**
     * Called when this Controller completes the process of being swapped in or out of the host view.
     *
     * @param changeHandler The {@link ControllerChangeHandler} that's managing the swap
     * @param changeType    The type of change that occurred
     */
    protected void onChangeEnded(@NonNull ControllerChangeHandler changeHandler, @NonNull ControllerChangeType changeType) { }

    /**
     * Called when this Controller is attached to its host ViewGroup
     *
     * @param view The View for this Controller (passed for convenience)
     */
    protected void onAttach(@NonNull View view) { }

    /**
     * Called when this Controller is detached from its host ViewGroup
     *
     * @param view The View for this Controller (passed for convenience)
     */
    protected void onDetach(@NonNull View view) { }

    /**
     * Called when this Controller has been destroyed.
     */
    protected void onDestroy() { }

    /**
     * Called when this Controller's host Activity is started
     */
    protected void onActivityStarted(@NonNull Activity activity) { }

    /**
     * Called when this Controller's host Activity is resumed
     */
    protected void onActivityResumed(@NonNull Activity activity) { }

    /**
     * Called when this Controller's host Activity is paused
     */
    protected void onActivityPaused(@NonNull Activity activity) { }

    /**
     * Called when this Controller's host Activity is stopped
     */
    protected void onActivityStopped(@NonNull Activity activity) { }

    /**
     * Called to save this Controller's View state. As Views can be detached and destroyed as part of the
     * Controller lifecycle (ex: when another Controller has been pushed on top of it), care should be taken
     * to save anything needed to reconstruct the View.
     *
     * @param view     This Controller's View, passed for convenience
     * @param outState The Bundle into which the View state should be saved
     */
    protected void onSaveViewState(@NonNull View view, @NonNull Bundle outState) { }

    /**
     * Restores data that was saved in the {@link #onSaveViewState(View, Bundle)} method. This should be overridden
     * to restore the View's state to where it was before it was destroyed.
     *
     * @param view           This Controller's View, passed for convenience
     * @param savedViewState The bundle that has data to be restored
     */
    protected void onRestoreViewState(@NonNull View view, @NonNull Bundle savedViewState) { }

    /**
     * Called to save this Controller's state in the event that its host Activity is destroyed.
     *
     * @param outState The Bundle into which data should be saved
     */
    protected void onSaveInstanceState(@NonNull Bundle outState) { }

    /**
     * Restores data that was saved in the {@link #onSaveInstanceState(Bundle)} method. This should be overridden
     * to restore this Controller's state to where it was before it was destroyed.
     *
     * @param savedInstanceState The bundle that has data to be restored
     */
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) { }

    /**
     * Calls startActivity(Intent) from this Controller's host Activity.
     */
    public final void startActivity(@NonNull final Intent intent) {
        executeWithRouter(new RouterRequiringFunc() {
            @Override public void execute() { router.startActivity(intent); }
        });
    }

    /**
     * Calls startActivityForResult(Intent, int) from this Controller's host Activity.
     */
    public final void startActivityForResult(@NonNull final Intent intent, final int requestCode) {
        executeWithRouter(new RouterRequiringFunc() {
            @Override public void execute() { router.startActivityForResult(instanceId, intent, requestCode); }
        });
    }

    /**
     * Calls startActivityForResult(Intent, int, Bundle) from this Controller's host Activity.
     */
    public final void startActivityForResult(@NonNull final Intent intent, final int requestCode, @Nullable final Bundle options) {
        executeWithRouter(new RouterRequiringFunc() {
            @Override public void execute() { router.startActivityForResult(instanceId, intent, requestCode, options); }
        });
    }

    /**
     * Registers this Controller to handle onActivityResult responses. Calling this method is NOT
     * necessary when calling {@link #startActivityForResult(Intent, int)}
     *
     * @param requestCode The request code being registered for.
     */
    public final void registerForActivityResult(final int requestCode) {
        executeWithRouter(new RouterRequiringFunc() {
            @Override public void execute() { router.registerForActivityResult(instanceId, requestCode); }
        });
    }

    /**
     * Should be overridden if this Controller has called startActivityForResult and needs to handle
     * the result.
     *
     * @param requestCode The requestCode passed to startActivityForResult
     * @param resultCode  The resultCode that was returned to the host Activity's onActivityResult method
     * @param data        The data Intent that was returned to the host Activity's onActivityResult method
     */
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) { }

    /**
     * Calls requestPermission(String[], int) from this Controller's host Activity. Results for this request,
     * including {@link #shouldShowRequestPermissionRationale(String)} and
     * {@link #onRequestPermissionsResult(int, String[], int[])} will be forwarded back to this Controller by the system.
     */
    @TargetApi(Build.VERSION_CODES.M)
    public final void requestPermissions(@NonNull final String[] permissions, final int requestCode) {
        requestedPermissions.addAll(Arrays.asList(permissions));

        executeWithRouter(new RouterRequiringFunc() {
            @Override public void execute() { router.requestPermissions(instanceId, permissions, requestCode); }
        });
    }

    /**
     * Gets whether you should show UI with rationale for requesting a permission.
     * {@see android.app.Activity#shouldShowRequestPermissionRationale(String)}
     *
     * @param permission A permission this Controller has requested
     */
    public boolean shouldShowRequestPermissionRationale(@NonNull String permission) {
        return false;
    }

    /**
     * Should be overridden if this Controller has requested runtime permissions and needs to handle the user's response.
     *
     * @param requestCode  The requestCode that was used to request the permissions
     * @param permissions  The array of permissions requested
     * @param grantResults The results for each permission requested
     */
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { }

    /**
     * Should be overridden if this Controller needs to handle the back button being pressed.
     *
     * @return True if this Controller has consumed the back button press, otherwise false
     */
    public boolean handleBack() {
        for (int i = childBackstack.size() - 1; i >= 0; i--) {
            Controller childController = childBackstack.get(i);
            if (childController.isAttached() && childController.getRouter().handleBack()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Adds a listener for all of this Controller's lifecycle events
     *
     * @param lifecycleListener The listener
     */
    public void addLifecycleListener(@NonNull LifecycleListener lifecycleListener) {
        if (!lifecycleListeners.contains(lifecycleListener)) {
            lifecycleListeners.add(lifecycleListener);
        }
    }

    /**
     * Removes a previously added lifecycle listener
     *
     * @param lifecycleListener The listener to be removed
     */
    public void removeLifecycleListener(@NonNull LifecycleListener lifecycleListener) {
        lifecycleListeners.remove(lifecycleListener);
    }

    /**
     * Returns this Controller's {@link RetainViewMode}. Defaults to {@link RetainViewMode#RELEASE_DETACH}.
     */
    @NonNull
    public RetainViewMode getRetainViewMode() {
        return retainViewMode;
    }

    /**
     * Sets this Controller's {@link RetainViewMode}, which will influence when its view will be released.
     * This is useful when a Controller's view hierarchy is expensive to tear down and rebuild.
     */
    public void setRetainViewMode(@NonNull RetainViewMode retainViewMode) {
        this.retainViewMode = retainViewMode != null ? retainViewMode : RetainViewMode.RELEASE_DETACH;
        if (this.retainViewMode == RetainViewMode.RELEASE_DETACH && !attached) {
            removeViewReference();
        }
    }

    /**
     * Returns the {@link ControllerChangeHandler} that should be used for pushing this Controller, or null
     * if the handler from the {@link RouterTransaction} should be used instead.
     */
    @Nullable
    public final ControllerChangeHandler getOverriddenPushHandler() {
        return overriddenPushHandler;
    }

    /**
     * Overrides the {@link ControllerChangeHandler} that should be used for pushing this Controller. If this is a
     * non-null value, it will be used instead of the handler from  the {@link RouterTransaction}.
     */
    public void overridePushHandler(@Nullable ControllerChangeHandler overriddenPushHandler) {
        this.overriddenPushHandler = overriddenPushHandler;
    }

    /**
     * Returns the {@link ControllerChangeHandler} that should be used for popping this Controller, or null
     * if the handler from the {@link RouterTransaction} should be used instead.
     */
    @Nullable
    public ControllerChangeHandler getOverriddenPopHandler() {
        return overriddenPopHandler;
    }

    /**
     * Overrides the {@link ControllerChangeHandler} that should be used for popping this Controller. If this is a
     * non-null value, it will be used instead of the handler from  the {@link RouterTransaction}.
     */
    public void overridePopHandler(@Nullable ControllerChangeHandler overriddenPopHandler) {
        this.overriddenPopHandler = overriddenPopHandler;
    }

    /**
     * Registers/unregisters for participation in populating the options menu by receiving options-related
     * callbacks, such as {@link #onCreateOptionsMenu(Menu, MenuInflater)}
     *
     * @param hasOptionsMenu If true, this controller's options menu callbacks will be called.
     */
    public final void setHasOptionsMenu(boolean hasOptionsMenu) {
        boolean invalidate = attached && !optionsMenuHidden && this.hasOptionsMenu != hasOptionsMenu;

        this.hasOptionsMenu = hasOptionsMenu;

        if (invalidate) {
            router.invalidateOptionsMenu();
        }
    }

    /**
     * Sets whether or not this controller's menu items should be visible. This is useful for hiding the
     * controller's options menu items when its UI is hidden, and not just when it is detached from the
     * window (the default).
     *
     * @param optionsMenuHidden Defaults to false. If true, this controller's menu items will not be shown.
     */
    public final void setOptionsMenuHidden(boolean optionsMenuHidden) {
        boolean invalidate = attached && hasOptionsMenu && this.optionsMenuHidden != optionsMenuHidden;

        this.optionsMenuHidden = optionsMenuHidden;

        if (invalidate) {
            router.invalidateOptionsMenu();
        }
    }

    /**
     * Adds option items to the host Activity's standard options menu. This will only be called if
     * {@link #setHasOptionsMenu(boolean)} has been called.
     *
     * @param menu The menu into which your options should be placed.
     * @param inflater The inflater that can be used to inflate your menu items.
     */
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) { }

    /**
     * Prepare the screen's options menu to be displayed. This is called directly before showing the
     * menu and can be used modify its contents.
     *
     * @param menu The menu that will be displayed
     */
    public void onPrepareOptionsMenu(@NonNull Menu menu) { }

    /**
     * Called when an option menu item has been selected by the user.
     *
     * @param item The selected item.
     * @return True if this event has been consumed, false if it has not.
     */
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return false;
    }

    final void setNeedsAttach() {
        needsAttach = true;
    }

    final void prepareForHostDetach() {
        needsAttach = needsAttach || attached;

        for (ControllerHostedRouter router : childRouters) {
            router.prepareForHostDetach();
        }
    }

    final boolean getNeedsAttach() {
        return needsAttach;
    }

    final boolean didRequestPermission(@NonNull String permission) {
        return requestedPermissions.contains(permission);
    }

    final void requestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        requestedPermissions.removeAll(Arrays.asList(permissions));
        onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    final void setRouter(@NonNull Router router) {
        if (this.router != router) {
            this.router = router;

            performOnRestoreInstanceState();

            for (RouterRequiringFunc listener : onRouterSetListeners) {
                listener.execute();
            }
            onRouterSetListeners.clear();
        } else {
            performOnRestoreInstanceState();
        }
    }

    final void executeWithRouter(@NonNull RouterRequiringFunc listener) {
        if (router != null) {
            listener.execute();
        } else {
            onRouterSetListeners.add(listener);
        }
    }

    final void activityStarted(@NonNull Activity activity) {
        onActivityStarted(activity);
    }

    final void activityResumed(@NonNull Activity activity) {
        if (!attached && view != null && viewIsAttached) {
            attach(view);
        } else if (attached) {
            needsAttach = false;
            hasSavedViewState = false;
        }

        onActivityResumed(activity);
    }

    final void activityPaused(@NonNull Activity activity) {
        onActivityPaused(activity);
    }

    final void activityStopped(@NonNull Activity activity) {
        onActivityStopped(activity);
    }

    final void activityDestroyed(boolean isChangingConfigurations) {
        if (isChangingConfigurations) {
            detach(view, true);
        } else {
            destroy(true);
        }
    }

    private void attach(@NonNull View view) {
        attachedToUnownedParent = router == null || view.getParent() != router.container;
        if (attachedToUnownedParent) {
            return;
        }

        hasSavedViewState = false;

        List<LifecycleListener> listeners = new ArrayList<>(lifecycleListeners);
        for (LifecycleListener lifecycleListener : listeners) {
            lifecycleListener.preAttach(this, view);
        }

        attached = true;
        needsAttach = false;

        onAttach(view);

        if (hasOptionsMenu && !optionsMenuHidden) {
            router.invalidateOptionsMenu();
        }

        listeners = new ArrayList<>(lifecycleListeners);
        for (LifecycleListener lifecycleListener : listeners) {
            lifecycleListener.postAttach(this, view);
        }
    }

    void detach(@NonNull View view, boolean forceViewRefRemoval) {
        if (!attachedToUnownedParent) {
            for (ControllerHostedRouter router : childRouters) {
                router.prepareForHostDetach();
            }
        }

        final boolean removeViewRef = forceViewRefRemoval || retainViewMode == RetainViewMode.RELEASE_DETACH || isBeingDestroyed;

        if (attached) {
            List<LifecycleListener> listeners = new ArrayList<>(lifecycleListeners);
            for (LifecycleListener lifecycleListener : listeners) {
                lifecycleListener.preDetach(this, view);
            }

            attached = false;
            onDetach(view);

            if (hasOptionsMenu && !optionsMenuHidden) {
                router.invalidateOptionsMenu();
            }

            listeners = new ArrayList<>(lifecycleListeners);
            for (LifecycleListener lifecycleListener : listeners) {
                lifecycleListener.postDetach(this, view);
            }
        }

        if (removeViewRef) {
            removeViewReference();
        }
    }

    private void removeViewReference() {
        if (view != null) {
            if (!isBeingDestroyed && !hasSavedViewState) {
                saveViewState(view);
            }

            List<LifecycleListener> listeners = new ArrayList<>(lifecycleListeners);
            for (LifecycleListener lifecycleListener : listeners) {
                lifecycleListener.preDestroyView(this, view);
            }

            onDestroyView(view);

            viewAttachHandler.unregisterAttachListener(view);
            viewAttachHandler = null;
            viewIsAttached = false;

            if (isBeingDestroyed) {
                destroyedView = new WeakReference<>(view);
            }
            view = null;

            listeners = new ArrayList<>(lifecycleListeners);
            for (LifecycleListener lifecycleListener : listeners) {
                lifecycleListener.postDestroyView(this);
            }

            for (ControllerHostedRouter childRouter : childRouters) {
                childRouter.removeHost();
            }
        }

        if (isBeingDestroyed) {
            performDestroy();
        }
    }

    final View inflate(@NonNull ViewGroup parent) {
        if (view != null && view.getParent() != null && view.getParent() != parent) {
            detach(view, true);
            removeViewReference();
        }

        if (view == null) {
            List<LifecycleListener> listeners = new ArrayList<>(lifecycleListeners);
            for (LifecycleListener lifecycleListener : listeners) {
                lifecycleListener.preCreateView(this);
            }

            view = onCreateView(LayoutInflater.from(parent.getContext()), parent);
            if (view == parent) {
                throw new IllegalStateException("Controller's onCreateView method returned the parent ViewGroup. Perhaps you forgot to pass false for LayoutInflater.inflate's attachToRoot parameter?");
            }

            listeners = new ArrayList<>(lifecycleListeners);
            for (LifecycleListener lifecycleListener : listeners) {
                lifecycleListener.postCreateView(this, view);
            }

            restoreViewState(view);

            viewAttachHandler = new ViewAttachHandler(new ViewAttachListener() {
                @Override
                public void onAttached(View v) {
                    if (v == view) {
                        viewIsAttached = true;
                        viewWasDetached = false;
                    }
                    attach(v);
                }

                @Override
                public void onDetached(View v) {
                    viewIsAttached = false;
                    viewWasDetached = true;

                    if (!isDetachFrozen) {
                        detach(v, false);
                    }
                }
            });
            viewAttachHandler.listenForAttach(view);
        } else if (retainViewMode == RetainViewMode.RETAIN_DETACH) {
            restoreChildControllerHosts();
        }

        return view;
    }

    private void restoreChildControllerHosts() {
        for (ControllerHostedRouter childRouter : childRouters) {
            if (!childRouter.hasHost()) {
                View containerView = view.findViewById(childRouter.getHostId());

                if (containerView != null && containerView instanceof ViewGroup) {
                    childRouter.setHost(this, (ViewGroup)containerView);
                    monitorChildRouter(childRouter);
                    childRouter.rebindIfNeeded();
                }
            }
        }
    }

    private void performDestroy() {
        if (!destroyed) {
            List<LifecycleListener> listeners = new ArrayList<>(lifecycleListeners);
            for (LifecycleListener lifecycleListener : listeners) {
                lifecycleListener.preDestroy(this);
            }

            destroyed = true;

            onDestroy();

            parentController = null;

            listeners = new ArrayList<>(lifecycleListeners);
            for (LifecycleListener lifecycleListener : listeners) {
                lifecycleListener.postDestroy(this);
            }
        }
    }

    final void destroy() {
        destroy(false);
    }

    private void destroy(boolean removeViews) {
        isBeingDestroyed = true;

        if (router != null) {
            router.unregisterForActivityResults(instanceId);
        }

        for (ControllerHostedRouter childRouter : childRouters) {
            childRouter.destroy(false);
        }

        if (!attached) {
            removeViewReference();
        } else if (removeViews) {
            detach(view, true);
        }
    }

    private void saveViewState(@NonNull View view) {
        hasSavedViewState = true;

        viewState = new Bundle(getClass().getClassLoader());

        SparseArray<Parcelable> hierarchyState = new SparseArray<>();
        view.saveHierarchyState(hierarchyState);
        viewState.putSparseParcelableArray(KEY_VIEW_STATE_HIERARCHY, hierarchyState);

        Bundle stateBundle = new Bundle();
        onSaveViewState(view, stateBundle);
        viewState.putBundle(KEY_VIEW_STATE_BUNDLE, stateBundle);

        List<LifecycleListener> listeners = new ArrayList<>(lifecycleListeners);
        for (LifecycleListener lifecycleListener : listeners) {
            lifecycleListener.onSaveViewState(this, viewState);
        }
    }

    private void restoreViewState(@NonNull View view) {
        if (viewState != null) {
            view.restoreHierarchyState(viewState.getSparseParcelableArray(KEY_VIEW_STATE_HIERARCHY));
            onRestoreViewState(view, viewState.getBundle(KEY_VIEW_STATE_BUNDLE));

            restoreChildControllerHosts();

            List<LifecycleListener> listeners = new ArrayList<>(lifecycleListeners);
            for (LifecycleListener lifecycleListener : listeners) {
                lifecycleListener.onRestoreViewState(this, viewState);
            }
        }
    }

    final Bundle saveInstanceState() {
        if (!hasSavedViewState && view != null) {
            saveViewState(view);
        }

        Bundle outState = new Bundle();
        outState.putString(KEY_CLASS_NAME, getClass().getName());
        outState.putBundle(KEY_VIEW_STATE, viewState);
        outState.putBundle(KEY_ARGS, args);
        outState.putString(KEY_INSTANCE_ID, instanceId);
        outState.putString(KEY_TARGET_INSTANCE_ID, targetInstanceId);
        outState.putStringArrayList(KEY_REQUESTED_PERMISSIONS, requestedPermissions);
        outState.putBoolean(KEY_NEEDS_ATTACH, needsAttach || attached);
        outState.putInt(KEY_RETAIN_VIEW_MODE, retainViewMode.ordinal());

        if (overriddenPushHandler != null) {
            outState.putBundle(KEY_OVERRIDDEN_PUSH_HANDLER, overriddenPushHandler.toBundle());
        }
        if (overriddenPopHandler != null) {
            outState.putBundle(KEY_OVERRIDDEN_POP_HANDLER, overriddenPopHandler.toBundle());
        }

        ArrayList<Bundle> childBundles = new ArrayList<>();
        for (ControllerHostedRouter childRouter : childRouters) {
            Bundle routerBundle = new Bundle();
            childRouter.saveInstanceState(routerBundle);
            childBundles.add(routerBundle);
        }
        outState.putParcelableArrayList(KEY_CHILD_ROUTERS, childBundles);

        ArrayList<String> childBackstack = new ArrayList<>();
        for (Controller controller : this.childBackstack) {
            childBackstack.add(controller.instanceId);
        }
        outState.putStringArrayList(KEY_CHILD_BACKSTACK, childBackstack);

        Bundle savedState = new Bundle();
        onSaveInstanceState(savedState);

        List<LifecycleListener> listeners = new ArrayList<>(lifecycleListeners);
        for (LifecycleListener lifecycleListener : listeners) {
            lifecycleListener.onSaveInstanceState(this, savedState);
        }

        outState.putBundle(KEY_SAVED_STATE, savedState);

        return outState;
    }

    private void restoreInstanceState(@NonNull Bundle savedInstanceState) {
        viewState = savedInstanceState.getBundle(KEY_VIEW_STATE);
        if (viewState != null) {
            viewState.setClassLoader(getClass().getClassLoader());
        }

        instanceId = savedInstanceState.getString(KEY_INSTANCE_ID);
        targetInstanceId = savedInstanceState.getString(KEY_TARGET_INSTANCE_ID);
        requestedPermissions.addAll(savedInstanceState.getStringArrayList(KEY_REQUESTED_PERMISSIONS));
        overriddenPushHandler = ControllerChangeHandler.fromBundle(savedInstanceState.getBundle(KEY_OVERRIDDEN_PUSH_HANDLER));
        overriddenPopHandler = ControllerChangeHandler.fromBundle(savedInstanceState.getBundle(KEY_OVERRIDDEN_POP_HANDLER));
        needsAttach = savedInstanceState.getBoolean(KEY_NEEDS_ATTACH);
        retainViewMode = RetainViewMode.values()[savedInstanceState.getInt(KEY_RETAIN_VIEW_MODE, 0)];

        List<Bundle> childBundles = savedInstanceState.getParcelableArrayList(KEY_CHILD_ROUTERS);
        for (Bundle childBundle : childBundles) {
            ControllerHostedRouter childRouter = new ControllerHostedRouter();
            childRouter.restoreInstanceState(childBundle);
            monitorChildRouter(childRouter);
            childRouters.add(childRouter);
        }

        List<String> childBackstackIds = savedInstanceState.getStringArrayList(KEY_CHILD_BACKSTACK);
        for (String controllerId : childBackstackIds) {
            Controller childController = findController(controllerId);
            if (childController != null) {
                childBackstack.add(childController);
            }
        }

        this.savedInstanceState = savedInstanceState.getBundle(KEY_SAVED_STATE);
        performOnRestoreInstanceState();
    }

    private void performOnRestoreInstanceState() {
        if (savedInstanceState != null && router != null) {
            onRestoreInstanceState(savedInstanceState);

            List<LifecycleListener> listeners = new ArrayList<>(lifecycleListeners);
            for (LifecycleListener lifecycleListener : listeners) {
                lifecycleListener.onRestoreInstanceState(this, savedInstanceState);
            }

            savedInstanceState = null;
        }
    }

    final void changeStarted(@NonNull ControllerChangeHandler changeHandler, @NonNull ControllerChangeType changeType) {
        if (!changeType.isEnter) {
            for (ControllerHostedRouter router : childRouters) {
                router.setDetachFrozen(true);
            }
        }

        onChangeStarted(changeHandler, changeType);

        List<LifecycleListener> listeners = new ArrayList<>(lifecycleListeners);
        for (LifecycleListener lifecycleListener : listeners) {
            lifecycleListener.onChangeStart(this, changeHandler, changeType);
        }
    }

    final void changeEnded(@NonNull ControllerChangeHandler changeHandler, @NonNull ControllerChangeType changeType) {
        if (!changeType.isEnter) {
            for (ControllerHostedRouter router : childRouters) {
                router.setDetachFrozen(false);
            }
        }

        onChangeEnded(changeHandler, changeType);

        List<LifecycleListener> listeners = new ArrayList<>(lifecycleListeners);
        for (LifecycleListener lifecycleListener : listeners) {
            lifecycleListener.onChangeEnd(this, changeHandler, changeType);
        }

        if (isBeingDestroyed && !viewIsAttached && !attached && destroyedView != null) {
            View view = destroyedView.get();
            if (router.container != null && view != null && view.getParent() == router.container) {
                router.container.removeView(view);
            }
            destroyedView = null;
        }
    }

    final void setDetachFrozen(boolean frozen) {
        if (isDetachFrozen != frozen) {
            isDetachFrozen = frozen;

            for (ControllerHostedRouter router : childRouters) {
                router.setDetachFrozen(frozen);
            }

            if (!frozen && view != null && viewWasDetached) {
                detach(view, false);
            }
        }
    }

    final void createOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (attached && hasOptionsMenu && !optionsMenuHidden) {
            onCreateOptionsMenu(menu, inflater);
        }
    }

    final void prepareOptionsMenu(@NonNull Menu menu) {
        if (attached && hasOptionsMenu && !optionsMenuHidden) {
            onPrepareOptionsMenu(menu);
        }
    }

    final boolean optionsItemSelected(@NonNull MenuItem item) {
        return attached && hasOptionsMenu && !optionsMenuHidden && onOptionsItemSelected(item);
    }

    private void monitorChildRouter(@NonNull ControllerHostedRouter childRouter) {
        childRouter.setOnControllerPushedListener(onControllerPushedListener);
    }

    private void onChildControllerPushed(@NonNull Controller controller) {
        if (!childBackstack.contains(controller)) {
            childBackstack.add(controller);
            controller.addLifecycleListener(new LifecycleListener() {
                @Override
                public void postDestroy(@NonNull Controller controller) {
                    childBackstack.remove(controller);
                }
            });
        }
    }

    final void setParentController(@Nullable Controller controller) {
        parentController = controller;
    }

    private void ensureRequiredConstructor() {
        Constructor[] constructors = getClass().getConstructors();
        if (getBundleConstructor(constructors) == null && getDefaultConstructor(constructors) == null) {
            throw new RuntimeException(getClass() + " does not have a constructor that takes a Bundle argument or a default constructor. Controllers must have one of these in order to restore their states.");
        }
    }

    @Nullable
    private static Constructor getDefaultConstructor(@NonNull Constructor[] constructors) {
        for (Constructor constructor : constructors) {
            if (constructor.getParameterTypes().length == 0) {
                return constructor;
            }
        }
        return null;
    }

    @Nullable
    private static Constructor getBundleConstructor(@NonNull Constructor[] constructors) {
        for (Constructor constructor : constructors) {
            if (constructor.getParameterTypes().length == 1 && constructor.getParameterTypes()[0] == Bundle.class) {
                return constructor;
            }
        }
        return null;
    }

    /** Modes that will influence when the Controller will allow its view to be destroyed */
    public enum RetainViewMode {
        /** The Controller will release its reference to its view as soon as it is detached. */
        RELEASE_DETACH,
        /** The Controller will retain its reference to its view when detached, but will still release the reference when a config change occurs. */
        RETAIN_DETACH
    }

    /** Allows external classes to listen for lifecycle events in a Controller */
    public static abstract class LifecycleListener {

        public void onChangeStart(@NonNull Controller controller, @NonNull ControllerChangeHandler changeHandler, @NonNull ControllerChangeType changeType) { }
        public void onChangeEnd(@NonNull Controller controller, @NonNull ControllerChangeHandler changeHandler, @NonNull ControllerChangeType changeType) { }

        public void preCreateView(@NonNull Controller controller) { }
        public void postCreateView(@NonNull Controller controller, @NonNull View view) { }

        public void preAttach(@NonNull Controller controller, @NonNull View view) { }
        public void postAttach(@NonNull Controller controller, @NonNull View view) { }

        public void preDetach(@NonNull Controller controller, @NonNull View view) { }
        public void postDetach(@NonNull Controller controller, @NonNull View view) { }

        public void preDestroyView(@NonNull Controller controller, @NonNull View view) { }
        public void postDestroyView(@NonNull Controller controller) { }

        public void preDestroy(@NonNull Controller controller) { }
        public void postDestroy(@NonNull Controller controller) { }

        public void onSaveInstanceState(@NonNull Controller controller, @NonNull Bundle outState) { }
        public void onRestoreInstanceState(@NonNull Controller controller, @NonNull Bundle savedInstanceState) { }

        public void onSaveViewState(@NonNull Controller controller, @NonNull Bundle outState) { }
        public void onRestoreViewState(@NonNull Controller controller, @NonNull Bundle savedViewState) { }

    }

}
