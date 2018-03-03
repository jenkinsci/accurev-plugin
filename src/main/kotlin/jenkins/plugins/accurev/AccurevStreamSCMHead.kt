package jenkins.plugins.accurev

import jenkins.scm.api.SCMHead

class AccurevStreamSCMHead(name: String) : SCMHead(name) {
    override fun getPronoun(): String? {
        return Messages.AccurevSnapshotSCMHead_Pronoun()
    }
}
