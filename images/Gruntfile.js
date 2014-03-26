module.exports = function(grunt) {

  grunt.loadNpmTasks('grunt-haml');

  // Project configuration.
  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),
    haml: {
      generator: {
        files: {
          'tmp/generator_logo.html': 'generator_logo.haml',
          'tmp/generator_model.html': 'generator_model.haml'
        }
      }
    }
  });

  grunt.registerTask('default', ['haml']);
};
