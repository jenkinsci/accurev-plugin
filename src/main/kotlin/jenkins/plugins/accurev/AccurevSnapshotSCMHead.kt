package jenkins.plugins.accurev

import jenkins.scm.api.SCMHead

class AccurevSnapshotSCMHead(name: String) : SCMHead(name) {
    override fun getPronoun(): String? {
        return Messages.StreamSCMHead_Pronoun()
    }
}
