package coakka.samples.runtime.scenarios.customer.desktop

import coakka.samples.runtime.scenarios.customer.contract.CustomerDraft
import coakka.samples.runtime.scenarios.customer.contract.CustomerMessageTypes
import coakka.samples.runtime.scenarios.customer.contract.CustomerPayloadContract
import coakka.samples.runtime.scenarios.customer.contract.CustomerView
import coakka.samples.runtime.scenarios.customer.contract.DeadletterView
import coakka.samples.runtime.scenarios.customer.contract.DeleteCustomerRequest
import coakka.samples.runtime.scenarios.customer.contract.ListCustomersRequest
import coakka.samples.runtime.scenarios.customer.contract.ListResponse
import coakka.samples.runtime.scenarios.customer.contract.MutationResponse
import coakka.samples.runtime.scenarios.customer.contract.UpdateCustomerRequest
import coakka.v2.connector.ConnectorOrchestrator
import coakka.v2.connector.DeadletterException
import coakka.v2.connector.RuntimeClient
import coakka.v2.connector.RuntimeEndpointFlags
import coakka.v2.connector.RuntimeEndpointSpec
import coakka.v2.connector.RuntimeRouteSpec
import coakka.v2.connector.RuntimeStartSpec
import coakka.v2.connector.protocol.ConnectorDeliveryHint
import coakka.v2.connector.protocol.ConnectorEnvelope
import coakka.v2.connector.protocol.ConnectorPayloadFormat
import coakka.v2.connector.protocol.ConnectorPayloadIdentity
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import javax.swing.table.DefaultTableModel

private const val FRONTEND_TARGET = "samples.customer.frontend"
private const val STORE_TARGET = "samples.customer.store"
private const val STORE_HOST = "127.0.0.1"
private const val FRONTEND_PORT = 19151
private const val STORE_PORT = 19152
private const val GENERATION = 1L

fun main(args: Array<String>) {
    if (args.contains("--smoke")) {
        runBlocking { runSmoke() }
        return
    }

    EventQueue.invokeLater {
        val runtime = CustomerDesktopRuntime.start()
        val ui = CustomerDesktopFrame(runtime)
        Runtime.getRuntime().addShutdownHook(Thread { runtime.close() })
        ui.isVisible = true
    }
}

private object CustomerPayloads {
    val CREATE = identity(CustomerMessageTypes.CREATE_REQUEST)
    val UPDATE = identity(CustomerMessageTypes.UPDATE_REQUEST)
    val DELETE = identity(CustomerMessageTypes.DELETE_REQUEST)
    val LIST = identity(CustomerMessageTypes.LIST_REQUEST)
    val MUTATION_RESPONSE = identity(CustomerMessageTypes.MUTATION_RESPONSE)
    val LIST_RESPONSE = identity(CustomerMessageTypes.LIST_RESPONSE)

    private fun identity(messageType: String) = ConnectorPayloadIdentity(
        messageType = messageType,
        payloadSchemaVersion = CustomerPayloadContract.SCHEMA_VERSION,
        payloadFormat = payloadFormat(),
    )

    private fun payloadFormat(): ConnectorPayloadFormat = when (CustomerPayloadContract.FORMAT) {
        "JSON" -> ConnectorPayloadFormat.JSON
        else -> error("unsupported customer payload format: ${CustomerPayloadContract.FORMAT}")
    }
}

private class InMemoryCustomerStore {
    private val customers = ConcurrentHashMap<String, CustomerView>()
    private val revision = AtomicLong(0)

    fun create(customer: CustomerDraft): MutationResponse {
        val nextRevision = revision.incrementAndGet()
        customers[customer.id] = customer.toView(nextRevision)
        return mutation("create", customer.id, nextRevision)
    }

    fun update(customer: CustomerDraft): MutationResponse {
        val nextRevision = revision.incrementAndGet()
        customers[customer.id] = customer.toView(nextRevision)
        return mutation("update", customer.id, nextRevision)
    }

    fun delete(id: String): MutationResponse {
        customers.remove(id)
        val nextRevision = revision.incrementAndGet()
        return mutation("delete", id, nextRevision)
    }

    fun list(): ListResponse = ListResponse(
        customers = customers.values.sortedWith(compareBy<CustomerView> { it.id }.thenBy { it.revision }),
    )

    private fun CustomerDraft.toView(nextRevision: Long) = CustomerView(
        id = id,
        name = name,
        email = email,
        tier = tier,
        notes = notes,
        revision = nextRevision,
    )

    private fun mutation(operation: String, id: String, nextRevision: Long) = MutationResponse(
        status = "ACCEPTED",
        operation = operation,
        customerId = id,
        revision = nextRevision,
        handledBy = "customer-desktop-store-local-handler",
    )
}

private data class ReplyPayload(val payload: Any, val identity: ConnectorPayloadIdentity)

private class CustomerDesktopRuntime private constructor(
    private val store: InMemoryCustomerStore,
    val orchestrator: ConnectorOrchestrator,
    val storeOrchestrator: ConnectorOrchestrator,
) : AutoCloseable {
    private val objectMapper = jacksonObjectMapper()
    private val closed = AtomicBoolean(false)

    companion object {
        fun start(): CustomerDesktopRuntime {
            val store = InMemoryCustomerStore()
            val storeOrchestrator = ConnectorOrchestrator.start(
                startSpec = RuntimeStartSpec(
                    systemName = "customer-desktop-store",
                    nodeId = "customer-desktop-store-node",
                    queueCapacity = 128,
                    strictNoDrop = true,
                    separateDeliveredRequestLane = true,
                    generation = GENERATION,
                    routes = listOf(
                        RuntimeRouteSpec(
                            target = STORE_TARGET,
                            endpoints = listOf(
                                RuntimeEndpointSpec(
                                    host = STORE_HOST,
                                    port = STORE_PORT,
                                    flags = RuntimeEndpointFlags.LOCAL,
	                                ),
	                            ),
	                        ),
	                        RuntimeRouteSpec(
	                            target = FRONTEND_TARGET,
	                            endpoints = listOf(
	                                RuntimeEndpointSpec(
	                                    host = STORE_HOST,
	                                    port = FRONTEND_PORT,
	                                    flags = 0,
	                                ),
	                            ),
	                        ),
	                    ),
	                ),
	            )
	            val frontendOrchestrator = ConnectorOrchestrator.start(
	                startSpec = RuntimeStartSpec(
	                    systemName = "customer-desktop-frontend",
	                    nodeId = "customer-desktop-frontend-node",
	                    queueCapacity = 128,
	                    strictNoDrop = true,
	                    separateDeliveredRequestLane = true,
	                    generation = GENERATION,
	                    routes = listOf(
	                        RuntimeRouteSpec(
	                            target = FRONTEND_TARGET,
	                            endpoints = listOf(
	                                RuntimeEndpointSpec(
	                                    host = STORE_HOST,
	                                    port = FRONTEND_PORT,
	                                    flags = RuntimeEndpointFlags.LOCAL,
	                                ),
	                            ),
	                        ),
	                        RuntimeRouteSpec(
	                            target = STORE_TARGET,
	                            endpoints = listOf(
	                                RuntimeEndpointSpec(
	                                    host = STORE_HOST,
	                                    port = STORE_PORT,
	                                    flags = 0,
	                                ),
	                            ),
	                        ),
	                    ),
	                ),
	            )
            val runtime = CustomerDesktopRuntime(store, frontendOrchestrator, storeOrchestrator)
            runtime.registerStoreHandler()
            return runtime
        }
    }

    suspend fun create(customer: CustomerDraft): MutationResponse = ask(
        payload = customer,
        identity = CustomerPayloads.CREATE,
        operation = "create_customer",
        responseType = MutationResponse::class.java,
    )

    suspend fun update(customer: CustomerDraft): MutationResponse = ask(
        payload = customer,
        identity = CustomerPayloads.UPDATE,
        operation = "update_customer",
        responseType = MutationResponse::class.java,
    )

    suspend fun delete(id: String): MutationResponse = ask(
        payload = DeleteCustomerRequest(id),
        identity = CustomerPayloads.DELETE,
        operation = "delete_customer",
        responseType = MutationResponse::class.java,
    )

    suspend fun list(): ListResponse = ask(
        payload = ListCustomersRequest(requestedBy = "customer-desktop-local"),
        identity = CustomerPayloads.LIST,
        operation = "list_customers",
        responseType = ListResponse::class.java,
    )

    suspend fun routeMiss(): DeadletterView {
        try {
            orchestrator.kotlin.ask(
                source = FRONTEND_TARGET,
                target = "samples.customer.missing",
                payloadUtf8 = """{"message":"missing customer route"}""",
                payloadIdentity = CustomerPayloads.LIST,
                timeoutMs = 2_000,
                operation = "route_miss",
                deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
            )
            error("expected route-miss deadletter")
        } catch (error: DeadletterException) {
            return DeadletterView(
                reason = error.deadletter.reason,
                target = error.deadletter.originalEnvelope.target,
                generation = error.deadletter.activeGeneration,
            )
        }
    }

    fun diagnosticsText(): String {
        val frontendInfo = orchestrator.runtimeInfo()
        val storeInfo = storeOrchestrator.runtimeInfo()
        val frontendConfig = orchestrator.runtimeConfig()
        val storeConfig = storeOrchestrator.runtimeConfig()
        val frontendStats = orchestrator.stats()
        val storeStats = storeOrchestrator.stats()
        val clientStats = orchestrator.clientStats()
        val storeClientStats = storeOrchestrator.clientStats()
        return buildString {
            appendLine("Frontend runtime")
            appendLine("  abi: ${frontendInfo.abiVersion}")
            appendLine("  version: ${frontendInfo.runtimeVersion}")
            appendLine("  git: ${frontendInfo.gitCommit}")
            appendLine("  system: ${frontendConfig.systemName}")
            appendLine("  node: ${frontendConfig.nodeId}")
            appendLine("  generation: ${frontendConfig.appliedGeneration}")
            appendLine()
            appendLine("Store runtime")
            appendLine("  abi: ${storeInfo.abiVersion}")
            appendLine("  version: ${storeInfo.runtimeVersion}")
            appendLine("  git: ${storeInfo.gitCommit}")
            appendLine("  system: ${storeConfig.systemName}")
            appendLine("  node: ${storeConfig.nodeId}")
            appendLine("  generation: ${storeConfig.appliedGeneration}")
            appendLine()
            appendLine("Route snapshot")
            appendLine("  frontend endpoint: $STORE_HOST:$FRONTEND_PORT LOCAL on frontend runtime")
            appendLine("  store endpoint: $STORE_HOST:$STORE_PORT LOCAL on store runtime")
            appendLine("  frontend routes: ${frontendConfig.routeCount}")
            appendLine("  store routes: ${storeConfig.routeCount}")
            appendLine()
            appendLine("Counters")
            appendLine("  store delivered: ${storeClientStats.deliveredRequests}")
            appendLine("  matched responses: ${clientStats.matchedResponses}")
            appendLine("  matched deadletters: ${clientStats.matchedDeadletters}")
            appendLine("  pending: ${clientStats.pendingRequests}")
            appendLine("  frontend route misses: ${frontendStats.routeMissCount}")
            appendLine("  frontend deadletters: ${frontendStats.deadletterCount}")
            appendLine("  store deadletters: ${storeStats.deadletterCount}")
        }
    }

	    override fun close() {
	        if (closed.compareAndSet(false, true)) {
	            runBlocking {
	                orchestrator.kotlin.shutdown()
	                storeOrchestrator.kotlin.shutdown()
	            }
	        }
	    }

	    private fun registerStoreHandler() {
	        storeOrchestrator.registerHandler(STORE_TARGET) { request ->
	            val reply = handleCustomerRequest(request)
	            RuntimeClient.replyTo(
                request = request,
                source = STORE_TARGET,
                payloadUtf8 = objectMapper.writeValueAsString(reply.payload),
                payloadIdentity = reply.identity,
            )
        }
    }

    private suspend fun <T : Any> ask(
        payload: Any,
        identity: ConnectorPayloadIdentity,
        operation: String,
        responseType: Class<T>,
    ): T {
	        val response = orchestrator.kotlin.ask(
            source = FRONTEND_TARGET,
            target = STORE_TARGET,
            payloadUtf8 = objectMapper.writeValueAsString(payload),
            payloadIdentity = identity,
            timeoutMs = 5_000,
            operation = operation,
	            deliveryHint = ConnectorDeliveryHint.REQUIRE_REMOTE,
	        )
        return objectMapper.readValue(response.payload, responseType)
    }

    private fun handleCustomerRequest(request: ConnectorEnvelope): ReplyPayload = when (request.messageType) {
        CustomerPayloads.CREATE.messageType -> {
            val customer = objectMapper.readValue(request.payload, CustomerDraft::class.java)
            ReplyPayload(store.create(customer), CustomerPayloads.MUTATION_RESPONSE)
        }
        CustomerPayloads.UPDATE.messageType -> {
            val customer = objectMapper.readValue(request.payload, CustomerDraft::class.java)
            ReplyPayload(store.update(customer), CustomerPayloads.MUTATION_RESPONSE)
        }
        CustomerPayloads.DELETE.messageType -> {
            val delete = objectMapper.readValue(request.payload, DeleteCustomerRequest::class.java)
            ReplyPayload(store.delete(delete.id), CustomerPayloads.MUTATION_RESPONSE)
        }
        CustomerPayloads.LIST.messageType -> {
            objectMapper.readValue(request.payload, ListCustomersRequest::class.java)
            ReplyPayload(store.list(), CustomerPayloads.LIST_RESPONSE)
        }
        else -> error("unsupported customer message type: ${request.messageType}")
    }
}

private class CustomerDesktopFrame(
    private val runtime: CustomerDesktopRuntime,
) : JFrame("CoAkka Customer Desktop Local Runtime") {
    private val worker = Executors.newSingleThreadExecutor()
    private val tableModel = DefaultTableModel(arrayOf("ID", "Name", "Email", "Tier", "Notes", "Rev"), 0)
    private val diagnostics = JTextArea(18, 34)
    private val log = JTextArea(10, 34)
    private val idField = JTextField("cust-001")
    private val nameField = JTextField("Ada Lovelace")
    private val emailField = JTextField("ada@example.com")
    private val tierField = JTextField("silver")
    private val notesField = JTextField("desktop local runtime")
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    init {
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        minimumSize = Dimension(1120, 720)
        contentPane = buildContent()
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent) {
                worker.shutdownNow()
                runtime.close()
            }
        })
        refreshCustomers("startup")
        refreshDiagnostics()
        pack()
        setLocationRelativeTo(null)
    }

    private fun buildContent(): JPanel {
        val root = JPanel(BorderLayout(12, 12))
        root.border = BorderFactory.createEmptyBorder(14, 14, 14, 14)
        root.add(headerPanel(), BorderLayout.NORTH)
        root.add(centerPanel(), BorderLayout.CENTER)
        return root
    }

    private fun headerPanel(): JPanel {
        val panel = JPanel(BorderLayout(8, 8))
        val title = JLabel("Customer Desktop Local Runtime")
        title.font = title.font.deriveFont(Font.BOLD, 22f)
        val path = JLabel(
            "Desktop UI -> $FRONTEND_TARGET -> CoAkka runtime ask -> $STORE_TARGET -> reply",
        )
        path.foreground = Color(52, 73, 94)
        val note = JLabel("One JVM process, two runtime handles, frontend talks to store by runtime messages, no store REST API.")
        note.foreground = Color(82, 95, 107)
        panel.add(title, BorderLayout.NORTH)
        panel.add(path, BorderLayout.CENTER)
        panel.add(note, BorderLayout.SOUTH)
        return panel
    }

    private fun centerPanel(): JSplitPane {
        val left = formPanel()
        val right = diagnosticsPanel()
        val table = JTable(tableModel)
        table.fillsViewportHeight = true
        val main = JPanel(BorderLayout(10, 10))
        main.add(left, BorderLayout.WEST)
        main.add(JScrollPane(table), BorderLayout.CENTER)
        return JSplitPane(JSplitPane.HORIZONTAL_SPLIT, main, right).apply {
            resizeWeight = 0.66
            border = BorderFactory.createEmptyBorder()
        }
    }

    private fun formPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.preferredSize = Dimension(320, 420)
        panel.border = BorderFactory.createTitledBorder("Customer command")
        val fields = listOf(
            "ID" to idField,
            "Name" to nameField,
            "Email" to emailField,
            "Tier" to tierField,
            "Notes" to notesField,
        )
        fields.forEachIndexed { index, (label, field) ->
            panel.add(
                JLabel(label),
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = index
                    anchor = GridBagConstraints.WEST
                    insets = Insets(4, 4, 4, 8)
                },
            )
            panel.add(
                field,
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = index
                    weightx = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(4, 4, 4, 4)
                },
            )
        }

        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
        buttons.add(JButton("Create").apply { addActionListener { mutate("create") { runtime.create(readDraft()) } } })
        buttons.add(JButton("Update").apply { addActionListener { mutate("update") { runtime.update(readDraft()) } } })
        buttons.add(JButton("Delete").apply { addActionListener { mutate("delete") { runtime.delete(idField.text.trim()) } } })
        buttons.add(JButton("Refresh").apply { addActionListener { refreshCustomers("manual_refresh") } })
        buttons.add(JButton("Route Miss").apply { addActionListener { triggerRouteMiss() } })
        panel.add(
            buttons,
            GridBagConstraints().apply {
                gridx = 0
                gridy = fields.size
                gridwidth = 2
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(10, 0, 0, 0)
            },
        )
        return panel
    }

    private fun diagnosticsPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        diagnostics.isEditable = false
        diagnostics.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        log.isEditable = false
        log.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        panel.add(JScrollPane(diagnostics).apply {
            border = BorderFactory.createTitledBorder("Runtime diagnostics")
        })
        panel.add(Box.createVerticalStrut(10))
        panel.add(JScrollPane(log).apply {
            border = BorderFactory.createTitledBorder("Message log")
        })
        return panel
    }

    private fun readDraft(): CustomerDraft = CustomerDraft(
        id = idField.text.trim(),
        name = nameField.text.trim(),
        email = emailField.text.trim(),
        tier = tierField.text.trim(),
        notes = notesField.text.trim(),
    )

    private fun mutate(label: String, action: suspend () -> MutationResponse) {
        runAsync {
            val response = action()
            appendLog("$label accepted id=${response.customerId} revision=${response.revision} via ${response.deliveryMode}")
            refreshCustomers(label)
        }
    }

    private fun refreshCustomers(source: String) {
        runAsync {
            val response = runtime.list()
            SwingUtilities.invokeLater {
                tableModel.rowCount = 0
                response.customers.forEach { customer ->
                    tableModel.addRow(
                        arrayOf<Any>(
                            customer.id,
                            customer.name,
                            customer.email,
                            customer.tier,
                            customer.notes,
                            customer.revision,
                        ),
                    )
                }
                appendLog("list after $source count=${response.customers.size} via ${response.deliveryMode}")
                refreshDiagnostics()
            }
        }
    }

    private fun triggerRouteMiss() {
        runAsync {
            val deadletter = runtime.routeMiss()
            appendLog("deadletter reason=${deadletter.reason} target=${deadletter.target} generation=${deadletter.generation}")
            refreshDiagnostics()
        }
    }

    private fun refreshDiagnostics() {
        diagnostics.text = runtime.diagnosticsText()
        diagnostics.caretPosition = 0
    }

    private fun appendLog(message: String) {
        SwingUtilities.invokeLater {
            log.append("${LocalTime.now().format(clockFormat)} $message\n")
            log.caretPosition = log.document.length
        }
    }

    private fun runAsync(action: suspend () -> Unit) {
        worker.submit {
            try {
                runBlocking { action() }
            } catch (error: Exception) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        this,
                        error.message ?: error.javaClass.name,
                        "Runtime operation failed",
                        JOptionPane.ERROR_MESSAGE,
                    )
                    appendLog("error ${error.javaClass.simpleName}: ${error.message}")
                    refreshDiagnostics()
                }
            }
        }
    }
}

private suspend fun runSmoke() {
    val runtime = CustomerDesktopRuntime.start()
    try {
        val info = runtime.orchestrator.runtimeInfo()
        println(
            "coakka_desktop_runtime_info version=${info.runtimeVersion} " +
                "git=${info.gitCommit}"
        )
        val create = runtime.create(
            CustomerDraft(
                id = "cust-001",
                name = "Ada Lovelace",
                email = "ada@example.com",
                tier = "silver",
                notes = "desktop-smoke",
            ),
        )
        check(create.deliveryMode == "runtime") { "expected runtime delivery for create" }
        println("coakka_desktop_create status=${create.status} revision=${create.revision}")

        val update = runtime.update(
            CustomerDraft(
                id = "cust-001",
                name = "Ada Lovelace",
                email = "ada@example.com",
                tier = "gold",
                notes = "desktop-smoke-updated",
            ),
        )
        check(update.deliveryMode == "runtime") { "expected runtime delivery for update" }
        println("coakka_desktop_update status=${update.status} revision=${update.revision}")

        val list = runtime.list()
        check(list.customers.single().tier == "gold") { "expected updated customer in list" }
        println("coakka_desktop_list count=${list.customers.size} tier=${list.customers.single().tier}")

        val delete = runtime.delete("cust-001")
        check(delete.deliveryMode == "runtime") { "expected runtime delivery for delete" }
        println("coakka_desktop_delete status=${delete.status} revision=${delete.revision}")

        val afterDelete = runtime.list()
        check(afterDelete.customers.isEmpty()) { "expected empty list after delete" }
        val deadletter = runtime.routeMiss()
        check(deadletter.reason == "DEADLETTER_REASON_ROUTE_MISS") {
            "expected route miss deadletter, got ${deadletter.reason}"
        }
        println("coakka_desktop_deadletter reason=${deadletter.reason} target=${deadletter.target}")

        val stats = runtime.orchestrator.clientStats()
        val storeStats = runtime.storeOrchestrator.clientStats()
        check(storeStats.deliveredRequests >= 5L) { "expected delivered requests, got ${storeStats.deliveredRequests}" }
        check(stats.matchedResponses >= 5L) { "expected matched responses, got ${stats.matchedResponses}" }
        check(stats.matchedDeadletters >= 1L) { "expected matched deadletters, got ${stats.matchedDeadletters}" }
        println(
            "coakka_desktop_stats storeDelivered=${storeStats.deliveredRequests} " +
                "matchedResponses=${stats.matchedResponses} matchedDeadletters=${stats.matchedDeadletters}"
        )
    } finally {
        runtime.close()
    }
}
