addWorkflow({

    "id": "file-copy-with-bad-output",

    "maxRetryCount": 0,

    "schedule": hourlySchedule(),

    "schedulingStrategy": serialSchedulingStrategy(),

    "trigger": hdfsCheckTrigger(
        "/user/celos/samples/file-copy-with-bad-output/input/${year}-${month}-${day}T${hour}00.txt",
        "hdfs://nn"
    ),

    "externalService": oozieExternalService(
        {
            "user.name": "celos",
            "oozie.wf.application.path": "/user/celos/samples/file-copy-with-bad-output/workflow/workflow.xml",
            "inputDir": "hdfs:/user/celos/samples/file-copy-with-bad-output/input",
            "outputDir": "hdfs:/user/celos/samples/file-copy-with-bad-output/output"
        },
        "http://nn:11000/oozie"
    )

});