/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.groovy

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked

import org.apache.lucene.util.AbstractRandomizedTest
import org.codehaus.groovy.reflection.ClassInfo
import org.elasticsearch.common.collect.ImmutableSet

import org.junit.Ignore

import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * {@code GroovyTestSanitizer} sanitizes the existing test infrastructure to work well with the Groovy runtime.
 * <ul>
 * <li>Modifies {@link AbstractRandomizedTest} to add Groovy's {@link ClassInfo} type to the list (literally it's a
 * {@code Set}) of types that are ignored during the cleanup process.</li>
 * </ul>
 * This should be invoked in a {@code static} block of every abstract test that uses the Elasticsearch test framework.
 * Since all tests are expected to extend either {@code ElasticsearchTestCase} or {@code ElasticsearchIntegrationTest},
 * {@link AbstractElasticsearchTestCase} and {@link AbstractElasticsearchIntegrationTest} exist to do this for you.
 * <pre>
 * static {
 *     assert GroovyTestSanitizer.groovySanitized
 * }
 * </pre>
 * In particular, this should be done before any test has the chance to run.
 * @see AbstractElasticsearchTestCase
 * @see AbstractElasticsearchIntegrationTest
 */
@Ignore
@CompileStatic
@TypeChecked
class GroovyTestSanitizer {
    /**
     * This will thread safely trigger any necessary action to appropriately ignore {@code static} Groovy baggage
     * during test cleanup. This is necessary to avoid test failures due to automatic static field monitoring for
     * improper test code cleanup (and therefore JVM waste and theoretically slower tests).
     * <pre>
     * static {
     *     assert GroovyTestSanitizer.groovySanitized
     * }
     * </pre>
     *
     * @return {@code true} to indicate that tests can be safely executed. Expected to always be {@code true}.
     */
    static boolean isGroovySanitized() {
        // This forces the class loader to load the inner type, thereby triggering its static block.
        // The JVM guarantees that this will only happen once, so we can lock-free do this as many times as we want
        //  and the static block will only ever be executed once.
        return GroovyTestSanitizerSingleton.SANITIZED
    }

    /**
     * An inner class is used to force the JVM to perform its classloader magic. This allows us to use it as though
     * it's locked without ever using locks ourselves.
     * <p />
     * It can be used as many times as
     */
    private static class GroovyTestSanitizerSingleton {

        private static final List<String> groovyNonIndyFields = ['$callSiteArray']

        /**
         * Currently, this modifies {@link AbstractRandomizedTest} to add {@link ClassInfo} to the set of known
         * static leak types to be ignored.
         */
        static {
            // this corresponds to a Set<String>
            Field field = AbstractRandomizedTest.class.getDeclaredField('STATIC_LEAK_IGNORED_TYPES')

            // the field is private static, so this allows us to mess with it
            field.accessible = true

            // the field is also final, so we need to remove that
            Field modifiersField = Field.class.getDeclaredField("modifiers")
            modifiersField.accessible = true
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL)

            // read the field
            Set<String> staticLeakIgnoreTypes = (Set<String>)field.get(null)
            // replace the field: add our own class to it, then replace it
            ImmutableSet.Builder<String> acceptedTypes = ImmutableSet.builder().addAll(staticLeakIgnoreTypes).add(ClassInfo.class.name)
            groovyNonIndyFields.each {
                acceptedTypes.add(AbstractElasticsearchIntegrationTest.getDeclaredField(it).type.name)
            }
            field.set(null, acceptedTypes.build())

            // reset it as final
            modifiersField.setInt(field, field.getModifiers() | Modifier.FINAL)
            modifiersField.accessible = false

            // reset it as private
            field.accessible = false
        }

        /**
         * An arbitrary field to allow the outer class to easily trigger the {@code static} block.
         */
        static final boolean SANITIZED = true
    }
}
