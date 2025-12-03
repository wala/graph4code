BEGIN {
    FS=":"
}

function checkAndPrint(src) {
	if (getline garbage < src >= 0) {
	    print("Application,Java,jarFile," src);
	    return 1
	} else {
	    return 0
	}

}

{
    for(i = 1; i <= NF; i++) {
	src = gensub(/.jar$/, "-shaded.jar", "g", $i);
	if (checkAndPrint(src) == 1) {
	  src = gensub(/.jar$/, "-sources.jar", "g", src);
	  checkAndPrint(src)
	} else {
	  checkAndPrint($i)
	  src = gensub(/.jar$/, "-sources.jar", "g", $i);
	  checkAndPrint(src)
	}
     }
}
