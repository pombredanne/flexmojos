/**
 * Copyright 2008 Marvin Herman Froeder
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
package org.sonatype.flexmojos.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.aspectj.lang.reflect.MethodSignature;

public aspect LazyLoadAspect
{

    pointcut getters() :
        execution(public * org.sonatype.flexmojos.common.AbstractMavenFlexCompilerConfiguration.get*()) ||
        execution(public * org.sonatype.flexmojos.compiler.CompcMojo.get*()) ||
        execution(public * org.sonatype.flexmojos.compiler.MxmlcMojo.get*()) ||
        execution(public * org.sonatype.flexmojos.compiler.AsdocMojo.get*());

    private Map<String, Object> cachedValues = new LinkedHashMap<String, Object>();

    Object around() : getters() {
        MethodSignature signature = (MethodSignature) thisJoinPoint.getSignature();
        String name = signature.getName();

        if ( !cachedValues.containsKey( name ) )
        {
            cachedValues.put( name, proceed() );
        }

        return cachedValues.get( name );
    }
}