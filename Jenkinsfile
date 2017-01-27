/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
node {
    def mvnHome
    stage('Preparation') {
        // Get the Maven tool.
        // NOTE: Needs to be configured in the global configuration.
        mvnHome = tool 'Maven'
    }
    stage('Build') {
        dir ('syntax-markdown') {
            checkout scm
            withEnv(["PATH+MAVEN=${mvnHome}/bin", 'MAVEN_OPTS=-Xmx1024m']) {
                try {
                    sh "mvn clean install jacoco:report -Pquality -U -e -Dmaven.test.failure.ignore"
                    currentBuild.result = 'SUCCESS'
                } catch (Exception err) {
                    currentBuild.result = 'FAILURE'
                    notifyByMail(currentBuild.result)
                    throw e
                }
            }
        }
    }
    stage('Post Build') {
        // Archive the generated artifacts
        archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
        // Save the JUnit test report
        step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
    }
}
def notifyByMail(String buildStatus) {
    buildStatus =  buildStatus ?: 'SUCCESSFUL'
    def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
    def summary = "${subject} (${env.BUILD_URL})"
    def details = """<p>STARTED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':</p>
    <p>Check console output at &QUOT;<a href='${env.BUILD_URL}'>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>&QUOT;</p>"""

    def to = emailextrecipients([
            [$class: 'CulpritsRecipientProvider'],
            [$class: 'DevelopersRecipientProvider'],
            [$class: 'RequesterRecipientProvider']
    ])
    if (to != null && !to.isEmpty()) {
        mail to: to, subject: subject, body: details
    }
}
