// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.stepping;

import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.statistics.Engine;
import com.intellij.debugger.statistics.StatisticsStorage;
import com.intellij.debugger.statistics.SteppingAction;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.Location;
import com.sun.jdi.request.StepRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerCoreBundle;

import static org.jetbrains.kotlin.idea.debugger.core.DebuggerUtil.isInSuspendMethod;

public final class KotlinStepActionFactory {
    private final static Logger LOG = Logger.getInstance(KotlinStepActionFactory.class);

    @NotNull
    public static DebugProcessImpl.StepOverCommand createKotlinStepOverCommand(
            DebugProcessImpl debugProcess,
            SuspendContextImpl suspendContext,
            boolean ignoreBreakpoints,
            @NotNull KotlinMethodFilter methodFilter,
            int stepSize
    ) {
        return debugProcess.new StepOverCommand(suspendContext, ignoreBreakpoints, methodFilter, stepSize) {
            @Override
            protected @NotNull String getStatusText() {
                return KotlinDebuggerCoreBundle.message("stepping.over.inline");
            }

            @Override
            public @Nullable LightOrRealThreadInfo getThreadFilterFromContext(@NotNull SuspendContextImpl suspendContext) {
                LightOrRealThreadInfo lightFilter = null;
                // for now use coroutine filtering only in suspend functions
                Location location = suspendContext.getLocation();
                if (location != null && isInSuspendMethod(location)) {
                    lightFilter = CoroutineJobInfo.extractJobInfo(suspendContext);
                }
                return lightFilter != null ? lightFilter : super.getThreadFilterFromContext(suspendContext);
            }

            @Override
            public @NotNull RequestHint getHint(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, @Nullable RequestHint parentHint) {
                KotlinStepOverRequestHint hint = new KotlinStepOverRequestHint(stepThread, suspendContext, methodFilter, parentHint, stepSize);
                hint.setResetIgnoreFilters(!debugProcess.getSession().shouldIgnoreSteppingFilters());
                hint.setRestoreBreakpoints(ignoreBreakpoints);
                try {
                    debugProcess.getSession().setIgnoreStepFiltersFlag(stepThread.frameCount());
                } catch (EvaluateException e) {
                    LOG.info(e);
                }
                return hint;
            }

            @Override
            public Object createCommandToken() {
                return StatisticsStorage.createSteppingToken(SteppingAction.STEP_OVER, Engine.KOTLIN);
            }
        };
    }

    @NotNull
    public static DebugProcessImpl.StepIntoCommand createKotlinStepIntoCommand(
            DebugProcessImpl debugProcess,
            SuspendContextImpl suspendContext,
            boolean ignoreBreakpoints,
            @Nullable MethodFilter methodFilter
    ) {
        return debugProcess.new StepIntoCommand(suspendContext, ignoreBreakpoints, methodFilter, StepRequest.STEP_LINE) {
            @Override
            public @NotNull RequestHint getHint(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, @Nullable RequestHint parentHint) {
                KotlinStepIntoRequestHint hint = new KotlinStepIntoRequestHint(stepThread, suspendContext, methodFilter, parentHint);
                hint.setResetIgnoreFilters(myMethodFilter != null && !debugProcess.getSession().shouldIgnoreSteppingFilters());
                return hint;
            }

            @Override
            public Object createCommandToken() {
                return StatisticsStorage.createSteppingToken(SteppingAction.STEP_INTO, Engine.KOTLIN);
            }
        };
    }

    @NotNull
    public static DebugProcessImpl.StepIntoCommand createStepIntoCommand(
            DebugProcessImpl debugProcess,
            SuspendContextImpl suspendContext,
            boolean ignoreFilters,
            @Nullable MethodFilter methodFilter,
            int stepSize
    ) {
        return debugProcess.new StepIntoCommand(suspendContext, ignoreFilters, methodFilter, stepSize) {
            @Override
            public @NotNull RequestHint getHint(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, @Nullable RequestHint parentHint) {
                RequestHint hint = new KotlinRequestHint(stepThread, suspendContext, stepSize, StepRequest.STEP_INTO, methodFilter, parentHint);
                hint.setResetIgnoreFilters(myMethodFilter != null && !debugProcess.getSession().shouldIgnoreSteppingFilters());
                return hint;
            }

            @Override
            public Object createCommandToken() {
                return StatisticsStorage.createSteppingToken(SteppingAction.STEP_INTO, Engine.KOTLIN);
            }
        };
    }

    @NotNull
    public static DebugProcessImpl.StepOutCommand createStepOutCommand(
            DebugProcessImpl debugProcess,
            SuspendContextImpl suspendContext
    ) {
        return debugProcess.new StepOutCommand(suspendContext, StepRequest.STEP_LINE) {
            @Override
            public @NotNull RequestHint getHint(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, @Nullable RequestHint parentHint) {
                RequestHint hint = new KotlinRequestHint(stepThread, suspendContext, StepRequest.STEP_LINE, StepRequest.STEP_OUT, null, parentHint);
                hint.setIgnoreFilters(debugProcess.getSession().shouldIgnoreSteppingFilters());
                return hint;
            }

            @Override
            public Object createCommandToken() {
                return StatisticsStorage.createSteppingToken(SteppingAction.STEP_OUT, Engine.KOTLIN);
            }
        };
    }
}
