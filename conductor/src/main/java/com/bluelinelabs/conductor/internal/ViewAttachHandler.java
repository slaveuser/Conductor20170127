package com.bluelinelabs.conductor.internal;

import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewGroup;

public class ViewAttachHandler {

    public interface ViewAttachListener {
        void onAttached(View view);
        void onDetached(View view);
    }

    private interface ChildAttachListener {
        void onAttached();
    }

    private ViewAttachListener attachListener;
    private OnAttachStateChangeListener rootOnAttachStateChangeListener = new OnAttachStateChangeListener() {
        boolean rootAttached = false;
        boolean childrenAttached = false;

        @Override
        public void onViewAttachedToWindow(final View v) {
            if (rootAttached) {
                return;
            }

            rootAttached = true;
            listenForDeepestChildAttach(v, new ChildAttachListener() {
                @Override
                public void onAttached() {
                    childrenAttached = true;
                    attachListener.onAttached(v);
                }
            });

        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            rootAttached = false;
            if (childrenAttached) {
                childrenAttached = false;
                attachListener.onDetached(v);
            }
        }
    };
    private OnAttachStateChangeListener childOnAttachStateChangeListener;

    public ViewAttachHandler(ViewAttachListener attachListener) {
        this.attachListener = attachListener;
    }

    public void listenForAttach(final View view) {
        view.addOnAttachStateChangeListener(rootOnAttachStateChangeListener);
    }

    public void unregisterAttachListener(View view) {
        view.removeOnAttachStateChangeListener(rootOnAttachStateChangeListener);

        if (childOnAttachStateChangeListener != null && view instanceof ViewGroup) {
            findDeepestChild((ViewGroup)view).removeOnAttachStateChangeListener(childOnAttachStateChangeListener);
        }
    }

    void listenForDeepestChildAttach(final View view, final ChildAttachListener attachListener) {
        if (!(view instanceof ViewGroup)) {
            attachListener.onAttached();
            return;
        }

        ViewGroup viewGroup = (ViewGroup)view;
        if (viewGroup.getChildCount() == 0) {
            attachListener.onAttached();
            return;
        }

        childOnAttachStateChangeListener = new OnAttachStateChangeListener() {
            boolean attached = false;

            @Override
            public void onViewAttachedToWindow(View v) {
                if (!attached) {
                    attached = true;
                    attachListener.onAttached();
                    v.removeOnAttachStateChangeListener(this);
                    childOnAttachStateChangeListener = null;
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) { }
        };
        findDeepestChild(viewGroup).addOnAttachStateChangeListener(childOnAttachStateChangeListener);
    }

    private View findDeepestChild(ViewGroup viewGroup) {
        if (viewGroup.getChildCount() == 0) {
            return viewGroup;
        }

        View lastChild = viewGroup.getChildAt(viewGroup.getChildCount() - 1);
        if (lastChild instanceof ViewGroup) {
            return findDeepestChild((ViewGroup)lastChild);
        } else {
            return lastChild;
        }
    }

}
