package bg.dev.camel.routes

import org.apache.camel.builder.RouteBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * DEMO 12 — Camel Route as an LLM Tool (spring-ai-tools: component)
 *
 * What it shows:
 *  - spring-ai-tools: URI scheme — register any Camel route as a Spring AI Function/Tool
 *  - The LLM decides autonomously when to invoke the tool based on the user query
 *  - Route receives tool parameters as Camel headers (itemSku) and returns a JSON string
 *  - In a real system, the route body would call a legacy SQL DB, SAP, or SFTP file
 *
 * Run: ./scripts/run-demo.sh 12
 */
@Component
@Profile('demo12')
class Demo12_SpringAiTools extends RouteBuilder {

    @Override
    void configure() {

      // Expose this route directly to Spring AI as an LLM Tool!
      from("spring-ai-tools:checkStock?tags=checkStock&description=Get current warehouse stock level for an item"
        + "&parameter.itemSku=string"
        + "&parameter.itemSku.description=The unique SKU of the item"
        + "&parameter.itemSku.required=true")
        .log("🤖 AI is invoking Camel tool for SKU: \${header.itemSku}")
        // In a real app, this would route to a legacy SQL db, SAP, or an SFTP file
        .choice()
          .when(header("itemSku").isEqualTo("SKU-99"))
            .setBody(constant("{ \"status\": \"In Stock\", \"quantity\": 42, \"warehouse\": \"Chicago\" }"))
          .otherwise()
            .setBody(constant("{ \"status\": \"Out of Stock\", \"quantity\": 0, \"warehouse\": \"N/A\" }"))
        .end()
        .marshal().json() // Return a clean JSON string back to the LLM
    }
}
