package com.bluelinelabs.conductor.changehandler;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import com.bluelinelabs.conductor.Controller;
import com.bluelinelabs.conductor.ControllerChangeHandler;

/**
 * A base {@link ControllerChangeHandler} that facilitates using {@link android.animation.Animator}s to replace Controller Views
 */
public abstract class AnimatorChangeHandler extends ControllerChangeHandler {

    private static final String KEY_DURATION = "AnimatorChangeHandler.duration";
    private static final String KEY_REMOVES_FROM_ON_PUSH = "AnimatorChangeHandler.removesFromViewOnPush";

    public static final long DEFAULT_ANIMATION_DURATION = -1;

    private long animationDuration;
    private boolean removesFromViewOnPush;
    private boolean canceled;
    private boolean needsImmediateCompletion;
    private boolean completed;
    private Animator animator;

    public AnimatorChangeHandler() {
        this(DEFAULT_ANIMATION_DURATION, true);
    }

    public AnimatorChangeHandler(boolean removesFromViewOnPush) {
        this(DEFAULT_ANIMATION_DURATION, removesFromViewOnPush);
    }

    public AnimatorChangeHandler(long duration) {
        this(duration, true);
    }

    public AnimatorChangeHandler(long duration, boolean removesFromViewOnPush) {
        animationDuration = duration;
        this.removesFromViewOnPush = removesFromViewOnPush;
    }

    @Override
    public void saveToBundle(@NonNull Bundle bundle) {
        super.saveToBundle(bundle);
        bundle.putLong(KEY_DURATION, animationDuration);
        bundle.putBoolean(KEY_REMOVES_FROM_ON_PUSH, removesFromViewOnPush);
    }

    @Override
    public void restoreFromBundle(@NonNull Bundle bundle) {
        super.restoreFromBundle(bundle);
        animationDuration = bundle.getLong(KEY_DURATION);
        removesFromViewOnPush = bundle.getBoolean(KEY_REMOVES_FROM_ON_PUSH);
    }

    @Override
    public void onAbortPush(@NonNull ControllerChangeHandler newHandler, @Nullable Controller newTop) {
        super.onAbortPush(newHandler, newTop);

        canceled = true;
        if (animator != null) {
            animator.cancel();
        }
    }

    @Override
    public void completeImmediately() {
        super.completeImmediately();

        needsImmediateCompletion = true;
        if (animator != null) {
            animator.end();
        }
    }

    public long getAnimationDuration() {
        return animationDuration;
    }

    @Override
    public boolean removesFromViewOnPush() {
        return removesFromViewOnPush;
    }

    /**
     * Should be overridden to return the Animator to use while replacing Views.
     *
     * @param container The container these Views are hosted in.
     * @param from The previous View in the container, if any.
     * @param to The next View that should be put in the container, if any.
     * @param isPush True if this is a push transaction, false if it's a pop.
     * @param toAddedToContainer True if the "to" view was added to the container as a part of this ChangeHandler. False if it was already in the hierarchy.
     */
    @NonNull
    protected abstract Animator getAnimator(@NonNull ViewGroup container, @Nullable View from, @Nullable View to, boolean isPush, boolean toAddedToContainer);

    /**
     * Will be called after the animation is complete to reset the View that was removed to its pre-animation state.
     */
    protected abstract void resetFromView(@NonNull View from);

    @Override
    public final void performChange(@NonNull final ViewGroup container, @Nullable final View from, @Nullable final View to, final boolean isPush, @NonNull final ControllerChangeCompletedListener changeListener) {
        boolean readyToAnimate = true;
        final boolean addingToView = to != null && to.getParent() == null;

        if (addingToView) {
            if (isPush || from == null) {
                container.addView(to);
            } else if (to.getParent() == null) {
                container.addView(to, container.indexOfChild(from));
            }

            if (to.getWidth() <= 0 && to.getHeight() <= 0) {
                readyToAnimate = false;
                to.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        final ViewTreeObserver observer = to.getViewTreeObserver();
                        if (observer.isAlive()) {
                            observer.removeOnPreDrawListener(this);
                        }
                        performAnimation(container, from, to, isPush, addingToView, changeListener);
                        return true;
                    }
                });
            }
        }

        if (readyToAnimate) {
            performAnimation(container, from, to, isPush, addingToView, changeListener);
        }
    }

    private void complete(@NonNull ControllerChangeCompletedListener changeListener, @Nullable AnimatorListener animatorListener) {
        if (!completed) {
            completed = true;
            changeListener.onChangeCompleted();
        }

        if (animator != null) {
            if (animatorListener != null) {
                animator.removeListener(animatorListener);
            }
            animator.cancel();
            animator = null;
        }
    }

    private void performAnimation(@NonNull final ViewGroup container, @Nullable final View from, @Nullable final View to, final boolean isPush, final boolean toAddedToContainer, @NonNull final ControllerChangeCompletedListener changeListener) {
        if (canceled) {
            complete(changeListener, null);
            return;
        }

        animator = getAnimator(container, from, to, isPush, toAddedToContainer);

        if (animationDuration > 0) {
            animator.setDuration(animationDuration);
        }

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                if (from != null && (!isPush || removesFromViewOnPush) && needsImmediateCompletion) {
                    container.removeView(from);
                }

                complete(changeListener, this);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!canceled && animator != null) {
                    if (from != null && (!isPush || removesFromViewOnPush)) {
                        container.removeView(from);
                    }

                    complete(changeListener, this);

                    if (isPush && from != null) {
                        resetFromView(from);
                    }
                }
            }
        });

        animator.start();
    }

}
