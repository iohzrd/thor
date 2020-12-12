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

package threads.thor.bt.module;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates individual message handlers,
 * that work with extended protocol message types.
 *
 * <p>Each message type is assigned a unique name,
 * thus annotated value should be a
 * <code>{@link java.util.Map}&lt;{@link String}, {@link threads.thor.bt.protocol.handler.MessageHandler}&lt;{@code ? extends}
 * {@link threads.thor.bt.protocol.extended.ExtendedMessage}&gt;&gt;</code>.
 *
 * @since 1.0
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtendedMessageHandlers {
}
