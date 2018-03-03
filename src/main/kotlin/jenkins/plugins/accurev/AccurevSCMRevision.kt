package jenkins.plugins.accurev

import jenkins.plugins.accurevclient.model.AccurevTransaction
import jenkins.scm.api.SCMHead
import jenkins.scm.api.SCMRevision
import java.util.Date

open class AccurevSCMRevision(head: SCMHead, val transaction: Long, val lastModified: Date) : SCMRevision(head) {
    override fun hashCode(): Int {
        return transaction.hashCode()
    }

    constructor(head: SCMHead, accurevTransaction: AccurevTransaction) : this(head, accurevTransaction.id, accurevTransaction.time)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) {
            return false
        }

        val that = other as AccurevSCMRevision

        return transaction == that.transaction && head == that.head
    }

    override fun toString(): String = transaction.toString()
}
