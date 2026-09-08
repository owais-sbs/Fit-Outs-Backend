package com.fitouts.subcontractor.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PackageAddendumRequest {

    private String title;
    private String description;
    private String attachmentPath;
}
