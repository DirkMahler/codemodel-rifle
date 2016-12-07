package hu.bme.mit.codemodel.rifle;

import com.buschmais.jqassistant.core.scanner.api.DefaultScope;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.jqassistant.plugin.common.test.AbstractPluginIT;
import com.buschmais.jqassistant.plugin.common.test.scanner.FileScannerIT;
import jline.internal.TestAccessible;
import org.junit.Test;

import java.io.File;

/**
 * Created by dirk.mahler on 07.12.2016.
 */
public class JavaScriptScannerPluginIT extends AbstractPluginIT {

    @Test
    public void scan() {
        store.beginTransaction();
        File classesDirectory = getClassesDirectory(FileScannerIT.class);
        FileDescriptor descriptor = getScanner().scan(classesDirectory, classesDirectory.getAbsolutePath(), DefaultScope.NONE);
        store.commitTransaction();
    }

}
