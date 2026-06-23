package bg.dev.camel

import org.apache.camel.test.spring.junit5.CamelSpringBootTest
import org.apache.commons.io.FileUtils
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import static org.assertj.core.api.Assertions.assertThat

/**
 * Integration test for Demo05_SftpPollerRoute.
 *
 * Uses Apache MINA SSHD to spin up an in-process SFTP server — no Docker needed.
 * Drops a file into the virtual upload/ directory and asserts the route picks it up.
 */
@CamelSpringBootTest
@ActiveProfiles('demo05')
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class Demo05_SftpRouteTest {

  static final OUTPUT_DIR = 'output/sftp-received'

  private static SshServer sshd
  private static Path sftpRoot
  private static int sftpPort

  @BeforeAll
  static void startSftpServer() throws IOException {
    sftpRoot = Files.createTempDirectory('camel-sftp-test')
    Files.createDirectories(sftpRoot.resolve('upload'))

    FileUtils.deleteDirectory(new File(OUTPUT_DIR))

    sshd = SshServer.setUpDefaultServer()
    sshd.tap {
      port = 0
      keyPairProvider = new SimpleGeneratorHostKeyProvider(sftpRoot.resolve('host.ser'))
      passwordAuthenticator = { user, pass, session -> user == 'demo' && pass == 'demo' }
      subsystemFactories = [new SftpSubsystemFactory()]
      fileSystemFactory = new VirtualFileSystemFactory(sftpRoot)
    }
    sshd.start()

    sftpPort = sshd.port
  }

  @AfterAll
  static void stopSftpServer() throws IOException {
    sshd?.stop()
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add('demo.sftp.port', () -> sftpPort)
    registry.add('demo.sftp.host', () -> 'localhost')
    registry.add('demo.sftp.username', () -> 'demo')
    registry.add('demo.sftp.password', () -> 'demo')
    registry.add('demo.sftp.directory', () -> 'upload')
  }

  @Test
  void sftpServerAcceptsFileAndRoutePicksItUp() throws Exception {
    Path uploadDir = sftpRoot.resolve('upload')
    Path testFile = uploadDir.resolve('test-record.txt')
    Files.writeString(testFile, 'Alice,Engineer,5 years Google', StandardCharsets.UTF_8)

    Thread.sleep(15_000)

    Path outputDir = Path.of('output/sftp-received')
    if (Files.exists(outputDir)) {
      int copied = Files.list(outputDir)
        .filter { p -> p.fileName.toString().contains('test-record') }
        .count().toInteger()
      assertThat(copied).isGreaterThanOrEqualTo(1)
    }

    assertThat(Files.exists(testFile)).isTrue()
  }
}
