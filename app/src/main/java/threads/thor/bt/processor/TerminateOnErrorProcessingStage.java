/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package threads.thor.bt.processor;


import threads.LogUtils;

/**
 * Base processing stage, that terminates processing chain in case of error.
 *
 * @param <C> Type of processing context
 * @since 1.5
 */
public abstract class TerminateOnErrorProcessingStage<C extends ProcessingContext> extends RoutingProcessingStage<C> {

    protected TerminateOnErrorProcessingStage(ProcessingStage<C> next) {
        super(next);
    }

    @Override
    protected final ProcessingStage<C> doExecute(C context, ProcessingStage<C> next) {
        try {
            doExecute(context);
        } catch (Exception e) {
            LogUtils.error(LogUtils.TAG, e);
            next = null; // terminate processing chain
        }
        return next;
    }

    /**
     * Perform processing. Implementations are free to throw exceptions,
     * in which case the processing chain will be terminated.
     *
     * @since 1.5
     */
    protected abstract void doExecute(C context);
}
