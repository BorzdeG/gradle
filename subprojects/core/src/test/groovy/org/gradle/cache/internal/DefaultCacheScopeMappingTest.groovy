/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.cache.internal

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.invocation.Gradle
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.initialization.layout.GlobalCacheDir
import org.gradle.initialization.layout.ProjectCacheDir
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class DefaultCacheScopeMappingTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def userHome = tmpDir.createDir("user-home")
    def rootDir = tmpDir.createDir("root")
    def gradleVersion = Stub(GradleVersion) {
        getVersion() >> "version"
    }
    def userHomeProvider = Stub(GradleUserHomeDirProvider) {
        getGradleUserHomeDirectory() >> userHome
    }
    def globalCacheDir = new GlobalCacheDir(userHomeProvider)
    def projectCacheDir = Stub(ProjectCacheDir) {
        getDir() >> rootDir
    }
    def mapping = new DefaultCacheScopeMapping(globalCacheDir, projectCacheDir, gradleVersion)

    def "null scope maps to user home directory"() {
        expect:
        mapping.getBaseDirectory(null, "key", VersionStrategy.CachePerVersion) == userHome.file("caches/version/key")
        mapping.getBaseDirectory(null, "key", VersionStrategy.SharedCache) == userHome.file("caches/key")
    }

    def "Gradle scope maps to build root directory"() {
        def gradle = Stub(Gradle)

        expect:
        mapping.getBaseDirectory(gradle, "key", VersionStrategy.CachePerVersion) == rootDir.file("version/key")
        mapping.getBaseDirectory(gradle, "key", VersionStrategy.SharedCache) == rootDir.file("key")
    }

    def "Project scope maps to child of build root directory"() {
        def rootProject = Stub(Project) {
            getPath() >> ":"
        }
        def childProject = Stub(Project) {
            getPath() >> ":child1:child2"
        }

        expect:
        mapping.getBaseDirectory(rootProject, "key", VersionStrategy.CachePerVersion) == rootDir.file("version/projects/_/key")
        mapping.getBaseDirectory(rootProject, "key", VersionStrategy.SharedCache) == rootDir.file("projects/_/key")
        mapping.getBaseDirectory(childProject, "key", VersionStrategy.CachePerVersion) == rootDir.file("version/projects/_child1_child2/key")
    }

    def "Task scope maps to child of build root directory"() {
        def task = Stub(Task) {
            getPath() >> ":project:task"
        }

        expect:
        mapping.getBaseDirectory(task, "key", VersionStrategy.CachePerVersion) == rootDir.file("version/tasks/_project_task/key")
        mapping.getBaseDirectory(task, "key", VersionStrategy.SharedCache) == rootDir.file("tasks/_project_task/key")
    }

    @Unroll
    def "can't use badly-formed key '#key'"() {
        when:
        mapping.getBaseDirectory(null, key, VersionStrategy.CachePerVersion)

        then:
        thrown(IllegalArgumentException)

        where:
        key << ["tasks", "projects", "1.11", "1.2.3.4", "", "/", "..", "c:/some-dir", "\n", "a\\b", " no white space "]
    }

    @Unroll
    def "can use well-formed key '#key'"() {
        when:
        mapping.getBaseDirectory(null, key, VersionStrategy.CachePerVersion)

        then:
        noExceptionThrown()

        where:
        key << ["abc", "a/b/c", "module-1.2"]
    }

    def "can locate cache root dir when no build scoped cache is used "() {
        def mapping = new DefaultCacheScopeMapping(globalCacheDir, null, gradleVersion)

        expect:
        mapping.getRootDirectory(null) == userHome.file("caches")
    }
}
