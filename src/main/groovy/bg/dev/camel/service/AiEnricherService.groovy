package bg.dev.camel.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service

@Service
class AiEnricherService {

  private final ChatClient chatClient
  private final ObjectMapper mapper

  AiEnricherService(ChatClient.Builder builder, ObjectMapper mapper) {
    this.chatClient = builder.build()
    this.mapper = mapper
  }

  Map<String, String> enrich(String csvLine) {
    if (!csvLine?.trim()) {
      return [name: '', role: '', summary: '', skills: '']
    }

    String[] parts = csvLine.split(',', 4)
    String name = parts.length > 0 ? parts[0].trim() : 'Unknown'
    String role = parts.length > 1 ? parts[1].trim() : 'Unknown'
    String background = parts.length > 2 ? parts[2].trim() : ''
    String extra = parts.length > 3 ? parts[3].trim() : ''

    String prompt = '''
      You are a professional profile enricher. Given a raw profile, return valid JSON only.

      Profile:
      Name: ${name}
      Role: ${role}
      Background: ${background} ${extra}
      
      Return this JSON structure (no markdown, no explanation):
      {
        "summary": "<two-sentence professional summary>",
        "skills": "<comma-separated list of 4-6 key technical skills>"
      }
    '''

    String json = chatClient.prompt()
      .user(prompt)
      .call()
      .content()

    if (!json?.trim()) {
      return [name: name, role: role, summary: background, skills: '']
    }

    try {
      def node = mapper.readTree(json.strip())
      return [
        name   : name,
        role   : role,
        summary: node.path('summary').asText(''),
        skills : node.path('skills').asText('')
      ]
    } catch (Exception e) {
      return [name: name, role: role, summary: background, skills: '']
    }
  }
}
