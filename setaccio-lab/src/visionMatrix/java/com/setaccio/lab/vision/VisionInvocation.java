package com.setaccio.lab.vision;

import com.setaccio.lab.model.UploadedImage;
import com.setaccio.lab.model.VisionInvocationResult;
import com.setaccio.lab.model.VisionInvocationSettings;

@FunctionalInterface
interface VisionInvocation {

    VisionInvocationResult invoke(UploadedImage image, VisionInvocationSettings settings);
}
