package jenkins.plugins.accurev

import hudson.model.TaskListener
import jenkins.scm.api.SCMHeadObserver
import jenkins.scm.api.SCMSource
import jenkins.scm.api.SCMSourceCriteria
import jenkins.scm.api.trait.SCMSourceContext

class AccurevSCMSourceContext(criteria: SCMSourceCriteria?, observer: SCMHeadObserver) : SCMSourceContext<AccurevSCMSourceContext, AccurevSCMSourceRequest>(criteria, observer) {
    override fun newRequest(source: SCMSource, listener: TaskListener?): AccurevSCMSourceRequest {
        return AccurevSCMSourceRequest(source, this, listener)
    }

    var wantStreams: Boolean = false
        private set
    var wantSnapshots: Boolean = false
        private set

    fun wantStreams(include: Boolean) : AccurevSCMSourceContext {
        wantStreams = wantStreams || include
        return this
    }

    fun wantSnapshots(include: Boolean) : AccurevSCMSourceContext {
        wantSnapshots = wantSnapshots || include
        return this
    }

}
