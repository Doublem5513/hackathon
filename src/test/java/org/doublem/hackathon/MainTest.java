package org.doublem.hackathon;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by mmatosevic on 17.2.2015.
 */
public class MainTest {
    @Test
    public void getMainClassName() throws Exception {
        String name = Main.NAME;
        Assert.assertEquals("org.doublem.hackathon.Main", name);
    }
}
