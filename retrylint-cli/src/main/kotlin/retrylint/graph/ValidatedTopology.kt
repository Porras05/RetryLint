package retrylint.graph

import retrylint.input.CallDeclaration
import retrylint.input.OperationDeclaration
import retrylint.input.ServiceDeclaration

data class ValidatedTopology(
    val servicesById: Map<String, ServiceDeclaration>,
    val operationsById: Map<String, OperationDeclaration>,
    val callsById: Map<String, CallDeclaration>,
    val outgoingCalls: Map<String, List<CallDeclaration>>,
    val topologicalOrder: List<String>,
)
