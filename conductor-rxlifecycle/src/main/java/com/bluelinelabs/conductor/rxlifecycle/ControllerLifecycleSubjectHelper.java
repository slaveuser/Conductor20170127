package com.bluelinelabs.conductor.rxlifecycle;

import android.support.annotation.NonNull;
import android.view.View;

import com.bluelinelabs.conductor.Controller;
import com.bluelinelabs.conductor.Controller.LifecycleListener;

import rx.subjects.BehaviorSubject;

/**
 * A simple utility class that will create a {@link BehaviorSubject} that calls onNext when events
 * occur in your {@link Controller}
 */
public class ControllerLifecycleSubjectHelper {

    private ControllerLifecycleSubjectHelper() { }

    public static BehaviorSubject<ControllerEvent> create(Controller controller) {
        final BehaviorSubject<ControllerEvent> subject = BehaviorSubject.create(ControllerEvent.CREATE);

        controller.addLifecycleListener(new LifecycleListener() {
            @Override
            public void preCreateView(@NonNull Controller controller) {
                subject.onNext(ControllerEvent.CREATE_VIEW);
            }

            @Override
            public void preAttach(@NonNull Controller controller, @NonNull View view) {
                subject.onNext(ControllerEvent.ATTACH);
            }

            @Override
            public void preDetach(@NonNull Controller controller, @NonNull View view) {
                subject.onNext(ControllerEvent.DETACH);
            }

            @Override
            public void preDestroyView(@NonNull Controller controller, @NonNull View view) {
                subject.onNext(ControllerEvent.DESTROY_VIEW);
            }

            @Override
            public void preDestroy(@NonNull Controller controller) {
                subject.onNext(ControllerEvent.DESTROY);
            }
        });

        return subject;
    }

}
