package bg.dev.camel.controller

import groovy.util.logging.Slf4j
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Slf4j
@RestController
@RequestMapping('/mock')
class MockSupplierController {

  @PostMapping('/supplier-order')
  Map<String, Object> placeOrder(@RequestBody Map<String, Object> request) {
    String partId = request.partId ?: 'UNKNOWN'
    // deterministic PO number derived from partId so demos are reproducible
    String poNumber = "PO-2026-${String.format('%05d', Math.abs(partId.hashCode()) % 99999)}"
    log.info("Mock Supplier API: received order for part '{}' → issuing {}", partId, poNumber)
    [poNumber: poNumber, partId: partId, deliveryDate: 'Tomorrow AM', status: 'CONFIRMED']
  }
}