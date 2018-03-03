package jenkins.plugins.accurev

import hudson.plugins.accurev.AccurevSCM
import jenkins.scm.api.SCMHead
import jenkins.scm.api.SCMRevision
import jenkins.scm.api.trait.SCMBuilder

class AccurevSCMBuilder<B : AccurevSCMBuilder<B>>(clazz: Class<AccurevSCM>?, head: SCMHead, revision: SCMRevision?) : SCMBuilder<B, AccurevSCM>(clazz, head, revision) {

    var serverUrl: String? = null
    var depot: String? = null
    var credentialsId: String? = null

    constructor(head: SCMHead, revision: SCMRevision?, serverUrl: String, depot: String, credentialsId: String?)
        : this(AccurevSCM::class.java, head, revision) {
        this.serverUrl = serverUrl
        this.depot = depot
        this.credentialsId = credentialsId
    }

    constructor(source: AccurevSCMSource, head: SCMHead, revision: SCMRevision?)
        : this(head, revision, source.serverUrl, source.depot, source.credentialsId)
    {

    }

    override fun build(): AccurevSCM {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
