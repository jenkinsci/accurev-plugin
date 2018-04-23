package jenkins.plugins.accurev

import hudson.Util
import hudson.model.TaskListener
import jenkins.plugins.accurevclient.model.AccurevStream
import jenkins.scm.api.SCMSource
import jenkins.scm.api.trait.SCMSourceRequest

class AccurevSCMSourceRequest(source: SCMSource, context: AccurevSCMSourceContext, listener: TaskListener?)
    : SCMSourceRequest(source, context, listener) {
    val isFetchStreams: Boolean
    val isFetchSnapshots: Boolean

    var streams: Iterable<AccurevStream> = listOf()
        get() = Util.fixNull(field)

    init {
        isFetchStreams = context.wantStreams
        isFetchSnapshots = context.wantSnapshots
        context.observer().includes
    }
}
