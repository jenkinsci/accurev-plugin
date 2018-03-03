package jenkins.plugins.accurev

import hudson.EnvVars
import hudson.Extension
import hudson.ExtensionList
import hudson.Util
import hudson.model.TaskListener
import hudson.scm.SCM
import jenkins.model.Jenkins
import jenkins.plugins.accurevclient.Accurev
import jenkins.plugins.accurevclient.model.AccurevDepot
import jenkins.plugins.accurevclient.model.AccurevStreamType
import jenkins.scm.api.SCMFileSystem
import jenkins.scm.api.SCMHead
import jenkins.scm.api.SCMHeadCategory
import jenkins.scm.api.SCMHeadEvent
import jenkins.scm.api.SCMHeadObserver
import jenkins.scm.api.SCMProbe
import jenkins.scm.api.SCMRevision
import jenkins.scm.api.SCMSource
import jenkins.scm.api.SCMSourceCriteria
import jenkins.scm.api.SCMSourceDescriptor
import jenkins.scm.api.trait.SCMSourceRequest
import jenkins.scm.api.trait.SCMSourceTrait
import jenkins.scm.impl.ChangeRequestSCMHeadCategory
import jenkins.scm.impl.TagSCMHeadCategory
import jenkins.scm.impl.UncategorizedSCMHeadCategory
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import java.util.*



class AccurevSCMSource
    @DataBoundConstructor constructor(val serverUrl: String, val depot: String)
    : SCMSource() {

    @set:DataBoundSetter var credentialsId: String? = null
    @set:DataBoundSetter var accurevTool: String? = null
    @Transient var accurevDepot: AccurevDepot? = null

    @set:DataBoundSetter var traits: List<SCMSourceTrait> = ArrayList()
        get() = Collections.unmodifiableList(field)
        set(value) {
            field = Util.fixNull(value)
        }

    override fun build(head: SCMHead, revision: SCMRevision?): SCM {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun retrieve(criteria: SCMSourceCriteria?, observer: SCMHeadObserver, event: SCMHeadEvent<*>?, listener: TaskListener) {

        val client = Accurev
            .with(listener, EnvVars(EnvVars.masterEnvVars))
            .at(Jenkins.getInstance().rootPath)
            .on(serverUrl)
            .client
        listener.logger.println("Looking up depot: $depot")
        accurevDepot = client.fetchDepot(depot)
        if (accurevDepot == null) {
            listener.logger.println("Could not find Depot")
        }
        AccurevSCMSourceContext(criteria, observer).withTraits(traits).newRequest(this, listener).use { request ->
            if (request.isFetchStreams) {
                val list = client.getStreams(depot).list.filter { it.type == AccurevStreamType.Normal || it.type == AccurevStreamType.Gated }
                request.streams = list
            }

            if (request.isFetchStreams) {
                var count = 0
                listener.logger.println("  Checking streams...")
                request.streams.forEach { stream ->
                    count++
                    listener.logger.println("     Checking stream ${stream.name}")
                    if (request.process(
                            AccurevStreamSCMHead(stream.name),
                            SCMSourceRequest.RevisionLambda<AccurevStreamSCMHead, AccurevSCMRevision> { head ->
                                val transaction = client.fetchTransaction(stream)
                                return@RevisionLambda AccurevSCMRevision(head, transaction)
                            },
                            SCMSourceRequest.ProbeLambda { head, revision ->
                                return@ProbeLambda createProbe(head, revision)
                            }
                        )
                    )
                }
            }
        }

    }

    override fun createProbe(head: SCMHead, revision: SCMRevision?): SCMProbe {
        ExtensionList.lookup(SCMFileSystem.Builder::class.java).get(AccurevSCMFileSystem.BuilderImpl::class.java)
    }

    @Extension class DescriptorImpl : SCMSourceDescriptor() {
        override fun getPronoun(): String {
            return Messages.AccurevSCMSource_RepositoryPronoun()
        }

        override fun getDisplayName(): String {
            return Messages.AccurevSCMSource_DisplayName()
        }

        override fun createCategories(): Array<SCMHeadCategory> {
            return arrayOf(UncategorizedSCMHeadCategory(
                Messages._AccurevSCMSource_StreamHeadCategory()
            ), ChangeRequestSCMHeadCategory(
                Messages._AccurevSCMSource_WorkspaceHeadCategory()
            ), UncategorizedSCMHeadCategory(
                Messages._AccurevSCMSource_GatedStreamHeadCategory()
            ), ChangeRequestSCMHeadCategory(
                Messages._AccurevSCMSource_StagingStreamHeadCategory()
            ), TagSCMHeadCategory(
                Messages._AccurevSCMSource_SnapshotHeadCategory()
            ))
        }
    }
}
