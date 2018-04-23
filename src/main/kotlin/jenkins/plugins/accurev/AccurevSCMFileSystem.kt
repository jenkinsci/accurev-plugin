package jenkins.plugins.accurev

import hudson.EnvVars
import hudson.model.Item
import hudson.plugins.accurev.AccurevSCM
import hudson.scm.SCM
import hudson.util.LogTaskListener
import jenkins.plugins.accurevclient.Accurev
import jenkins.plugins.accurevclient.AccurevClient
import jenkins.scm.api.SCMFile
import jenkins.scm.api.SCMFileSystem
import jenkins.scm.api.SCMHead
import jenkins.scm.api.SCMRevision
import jenkins.scm.api.SCMSource
import java.util.logging.Level
import java.util.logging.Logger

class AccurevSCMFileSystem(val client: AccurevClient, val depot: String, var stream: String, rev: SCMRevision?): SCMFileSystem(rev) {

    init {
        rev?.let {
            stream = it.head.name
        }
    }

    override fun getRevision(): AccurevSCMRevision {
        return super.getRevision() as AccurevSCMRevision
    }

    override fun lastModified(): Long {
        return revision.lastModified.time
    }

    override fun getRoot(): SCMFile {
        return AccurevSCMFile(this)
    }

    class BuilderImpl: Builder() {
        override fun supports(source: SCM?): Boolean {
            return source is AccurevSCM
                && source.server != null
        }

        override fun supports(source: SCMSource?): Boolean {
            return source is AccurevSCMSource
        }

        override fun build(owner: Item, scm: SCM, rev: SCMRevision?): SCMFileSystem? {
            if (rev != null && rev !is AccurevSCMRevision && scm !is AccurevSCM) return null
            val listener = LogTaskListener(LOGGER, Level.FINE)
            val accurevSCM = scm as AccurevSCM
            val depot = accurevSCM.depot
            val stream = accurevSCM.stream
            val tool = accurevSCM.resolveAccurevTool(listener)
            val accurev = Accurev.with(listener, EnvVars(EnvVars.masterEnvVars))
            tool?.home?.let { accurev.using(it) }
            accurevSCM.server?.let {
                accurev.on(it.url)
            }
            val client = accurev.client
            return AccurevSCMFileSystem(client, depot, stream, rev)
        }

        override fun build(source: SCMSource, head: SCMHead, rev: SCMRevision?): SCMFileSystem? {
            val accurevSCMSource = source as AccurevSCMSource
            val depot = accurevSCMSource.depot
            val stream = head.name
            val tool = accurevSource.
        }
    }

    companion object {
        private val LOGGER = Logger.getLogger(AccurevSCMFileSystem::class.java.name)
    }
}
