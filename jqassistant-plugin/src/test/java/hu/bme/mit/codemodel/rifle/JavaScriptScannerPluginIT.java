package hu.bme.mit.codemodel.rifle;

import com.buschmais.jqassistant.core.scanner.api.DefaultScope;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.jqassistant.plugin.common.test.AbstractPluginIT;
import com.buschmais.jqassistant.plugin.common.test.scanner.FileScannerIT;
import hu.bme.mit.codemodel.rifle.jqassistant.model.JavaScriptFileDescriptor;
import org.junit.Test;

import java.io.File;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;

/**
 * Created by dirk.mahler on 07.12.2016.
 */
public class JavaScriptScannerPluginIT extends AbstractPluginIT {

    @Test
    public void scan() {
        store.beginTransaction();
        File classesDirectory = getClassesDirectory(FileScannerIT.class);
        File testFile = new File(classesDirectory, "hello-world.js");
        FileDescriptor fileDescriptor = getScanner().scan(testFile, testFile.getAbsolutePath(), DefaultScope.NONE);
        assertThat(fileDescriptor, instanceOf(JavaScriptFileDescriptor.class));
        store.commitTransaction();
    }

}
