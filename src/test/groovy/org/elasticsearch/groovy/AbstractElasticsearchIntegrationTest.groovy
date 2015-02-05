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
import org.apache.lucene.util.AbstractRandomizedTest
import org.elasticsearch.common.collect.ImmutableSet
import org.elasticsearch.test.ElasticsearchIntegrationTest

import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * The basis for all Groovy client test classes that require a running Elasticsearch cluster / client (integration
 * tests).
 * @see AbstractElasticsearchTestCase
 */
@CompileStatic
abstract class AbstractElasticsearchIntegrationTest extends ElasticsearchIntegrationTest {
    /**
     * Sanitize the test code to allow Groovy to cleanup after itself rather than forcing the onus onto everyone else.
     */
    static {
        assert GroovyTestSanitizer.groovySanitized
        acceptGroovyNonIndyFields()
    }

    private static final List<String> groovyNonIndyFields = ['$callSiteArray']

    static void acceptGroovyNonIndyFields() {
        // this corresponds to a Set<String>
        Field field = AbstractRandomizedTest.getDeclaredField('STATIC_LEAK_IGNORED_TYPES')

        // the field is private static, so this allows us to mess with it
        field.accessible = true

        // the field is also final, so we need to remove that
        Field modifiersField = Field.class.getDeclaredField("modifiers")
        modifiersField.accessible = true
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL)

        // read the field
        Set<String> staticLeakIgnoreTypes = (Set<String>)field.get(null)
        // replace the field: add our own class to it, then replace it
        ImmutableSet.Builder<String> acceptedTypes = ImmutableSet.builder().addAll(staticLeakIgnoreTypes)
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
}
